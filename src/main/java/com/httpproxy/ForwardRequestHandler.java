package com.httpproxy;

import com.httpproxy.pojo.HttpResponseRecord;
import com.httpproxy.pojo.Packet;
import com.httpproxy.util.HttpSerializer;
import com.httpproxy.util.SocketProtocol;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/** 通用的请求处理器 (实现 HttpHandler 接口) */
@Slf4j
public class ForwardRequestHandler implements HttpHandler {
  private static final AtomicLong sessionIds = new AtomicLong(0);
  private static final LinkedBlockingQueue<Packet> packets = new LinkedBlockingQueue<>();
  private static final Map<Long, Function<Packet, Void>> tasks = new ConcurrentHashMap<>();

  /** Hop-by-hop 头，不应被代理转发 */
  private static final Set<String> HOP_BY_HOP_HEADERS =
      Set.of(
          "connection",
          "keep-alive",
          "proxy-authenticate",
          "proxy-authorization",
          "te",
          "trailer",
          "transfer-encoding",
          "upgrade");

  /** HTTP/2 伪头部，以冒号开头，不应转发给 HTTP/1.1 客户端 */
  private static boolean isHttp2PseudoHeader(String headerName) {
    return headerName.startsWith(":");
  }

  private SocketProtocol socketProtocols;

  ForwardRequestHandler() {}

  public SocketProtocol startReceive() {
    socketProtocols = Server.getSocketProtocols();
    new Thread(
            () -> {
              try {
                while (true) {
                  final Packet receive = socketProtocols.receive();
                  if (receive == null) {
                    continue;
                  }
                  packets.put(receive);
                }
              } catch (IOException | InterruptedException e) {
                log.error("Error receiving packet: {}", e.getMessage());
                throw new RuntimeException(e);
              }
            },
            "receive-packet")
        .start();
    new Thread(
            () -> {
              try {
                while (true) {
                  final Packet take = packets.take();
                  final Function<Packet, Void> runnable;
                  runnable = tasks.get(take.sessionId());
                  if (runnable != null) {
                    runnable.apply(take);
                  } else {
                    log.debug("No task found for sessionId: {}", take.sessionId());
                  }
                }
              } catch (InterruptedException e) {
                log.error("Error receiving packet: {}", e.getMessage());
                throw new RuntimeException(e);
              }
            },
            "httpxProxy-receive-apply")
        .start();
    return socketProtocols;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    // IP 白名单检查 - 在任何处理之前
    String clientIp = exchange.getRemoteAddress().toString();
    if (!IpWhitelist.isAllowed(clientIp)) {
      log.warn("Blocked request from {} (IP not in whitelist)", clientIp);
      exchange.getResponseHeaders().set("Content-Type", "text/plain");
      exchange.sendResponseHeaders(403, 0);
      exchange.getResponseBody().write("Forbidden: IP not allowed".getBytes());
      exchange.close();
      return;
    }

    final long sessionId = sessionIds.getAndIncrement();
    String requestMethod = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    log.info(
        "Received {} request for {} from {}", requestMethod, path, exchange.getRemoteAddress());

    byte[] request = HttpSerializer.serializeRequest(exchange);

    Packet packet = new Packet(sessionId, request);
    log.trace("Sending request data: {}", new String(request, StandardCharsets.UTF_8));
    socketProtocols.send(packet);

    tasks.put(sessionId, receiveCallback(exchange));
  }

  private Function<Packet, Void> receiveCallback(final HttpExchange exchange) {
    return receive -> {
      try {
        log.debug(
            "receiveCallback triggered for sessionId={}",
            receive != null ? receive.sessionId() : "null");
        log.trace(
            "Received response data: {}",
            receive != null ? new String(receive.data(), StandardCharsets.UTF_8) : "null");
        if (receive == null) {
          log.warn("receive is null, sending 500 error");
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
          return null;
        }

        HttpResponseRecord response = HttpSerializer.deserializeResponse(receive.data());

        log.debug(
            "Processing response: isSse={}, isSseEnd={}, statusCode={}, hasHeaders={}, hasBody={}",
            response.isSse(),
            response.isSseEnd(),
            response.statusCode(),
            response.headers() != null,
            response.body() != null);

        if (response.isSse()) {
          // SSE 流式响应
          if (!response.isSseEnd() && response.headers() != null) {
            // SSE 流开始 - 发送响应头
            log.debug("SSE stream start: setting headers");
            Headers responseHeaders = exchange.getResponseHeaders();
            for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
              String headerName = entry.getKey().toLowerCase();
              if (HOP_BY_HOP_HEADERS.contains(headerName)) {
                log.debug("Skipping hop-by-hop header: {}", headerName);
                continue;
              }
              if (isHttp2PseudoHeader(headerName)) {
                log.debug("Skipping HTTP/2 pseudo header: {}", headerName);
                continue;
              }
              for (String value : entry.getValue()) {
                responseHeaders.add(entry.getKey(), value);
                log.debug("Adding header: {}={}", entry.getKey(), value);
              }
            }
            log.debug(
                "SSE stream start: sending response headers with statusCode={}, contentLength=0 (chunked)",
                response.statusCode());
            exchange.sendResponseHeaders(response.statusCode(), 0); // chunked 模式
            log.debug("SSE stream start: headers sent successfully");
          } else if (!response.isSseEnd() && response.body() != null) {
            // SSE 事件 - 写入响应体
            log.debug("SSE event: writing body length={}", response.body().length);
            exchange.getResponseBody().write(response.body());
            exchange.getResponseBody().flush();
            log.debug("SSE event: body written and flushed");
          } else if (response.isSseEnd()) {
            // SSE 流结束 - 关闭连接
            log.debug("SSE stream end: closing exchange for sessionId={}", receive.sessionId());
            exchange.close();
            tasks.remove(receive.sessionId());
            log.debug("SSE stream end: task removed for sessionId={}", receive.sessionId());
            return null;
          }
          // SSE 流未结束，继续等待下一个包
          log.debug("SSE stream continuing, waiting for next packet");
        } else {
          // 普通响应
          log.debug("Normal response: processing");
          if (response.headers() != null) {
            Headers responseHeaders = exchange.getResponseHeaders();
            for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
              String headerName = entry.getKey().toLowerCase();
              if (HOP_BY_HOP_HEADERS.contains(headerName)) {
                log.debug("Skipping hop-by-hop header: {}", headerName);
                continue;
              }
              if (isHttp2PseudoHeader(headerName)) {
                log.debug("Skipping HTTP/2 pseudo header: {}", headerName);
                continue;
              }
              for (String value : entry.getValue()) {
                responseHeaders.add(entry.getKey(), value);
                log.debug("Adding header: {}={}", entry.getKey(), value);
              }
            }
          }

          byte[] responseBody = response.body();
          int contentLength = responseBody == null ? 0 : responseBody.length;

          log.debug(
              "Normal response: statusCode={}, contentLength={}, hasBody={}",
              response.statusCode(),
              contentLength,
              responseBody != null);

          log.debug(
              "Normal response: exchange response headers before send: {}",
              exchange.getResponseHeaders());

          exchange.sendResponseHeaders(response.statusCode(), contentLength);
          log.debug("Normal response: headers sent");

          if (responseBody != null) {
            exchange.getResponseBody().write(responseBody);
            log.debug("Normal response: body written length={}", responseBody.length);
          }
          exchange.close();
          log.debug("Normal response: exchange closed for sessionId={}", receive.sessionId());
          tasks.remove(receive.sessionId());
          log.debug("Normal response: task removed for sessionId={}", receive.sessionId());
          return null;
        }
        return null;
      } catch (Throwable e) {
        log.error(
            "Error handling request: sessionId={}, error={}",
            receive != null ? receive.sessionId() : "null",
            e.getMessage(),
            e);
        try {
          exchange.close();
          if (receive != null) {
            tasks.remove(receive.sessionId());
          }
        } catch (Exception ex) {
          log.error("Error closing exchange: {}", ex.getMessage());
        }
        return null;
      }
    };
  }
}

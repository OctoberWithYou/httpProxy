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

  private final SocketProtocol socketProtocols;

  ForwardRequestHandler() {
    socketProtocols = Server.getSocketProtocols();
    new Thread(
            () -> {
              try {
                while (true) {
                  final Packet receive = socketProtocols.receive();
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
                    log.warn("No task found for sessionId: {}", take.sessionId());
                  }
                }
              } catch (InterruptedException e) {
                log.error("Error receiving packet: {}", e.getMessage());
                throw new RuntimeException(e);
              }
            },
            "httpxProxy-receive-apply")
        .start();
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
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
        log.trace(
            "Received response data: {}",
            receive != null ? new String(receive.data(), StandardCharsets.UTF_8) : "null");
        if (receive == null) {
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
          return null;
        }

        HttpResponseRecord response = HttpSerializer.deserializeResponse(receive.data());

        if (response.isSse()) {
          // SSE 流式响应
          if (!response.isSseEnd() && response.headers() != null) {
            // SSE 流开始 - 发送响应头
            Headers responseHeaders = exchange.getResponseHeaders();
            for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
              String headerName = entry.getKey().toLowerCase();
              if (HOP_BY_HOP_HEADERS.contains(headerName)) {
                continue;
              }
              for (String value : entry.getValue()) {
                responseHeaders.add(entry.getKey(), value);
              }
            }
            exchange.sendResponseHeaders(response.statusCode(), 0); // chunked 模式
          } else if (!response.isSseEnd() && response.body() != null) {
            // SSE 事件 - 写入响应体
            exchange.getResponseBody().write(response.body());
            exchange.getResponseBody().flush();
          } else if (response.isSseEnd()) {
            // SSE 流结束 - 关闭连接
            exchange.close();
            tasks.remove(receive.sessionId());
            return null;
          }
          // SSE 流未结束，继续等待下一个包
        } else {
          // 普通响应
          if (response.headers() != null) {
            Headers responseHeaders = exchange.getResponseHeaders();
            for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
              String headerName = entry.getKey().toLowerCase();
              if (HOP_BY_HOP_HEADERS.contains(headerName)) {
                continue;
              }
              for (String value : entry.getValue()) {
                responseHeaders.add(entry.getKey(), value);
              }
            }
          }

          byte[] responseBody = response.body();
          int contentLength = responseBody == null ? 0 : responseBody.length;

          exchange.sendResponseHeaders(response.statusCode(), contentLength);

          if (responseBody != null) {
            exchange.getResponseBody().write(responseBody);
          }
          exchange.close();
          return null;
        }
        return null;
      } catch (Throwable e) {
        log.error("Error handling request: {}", e.getMessage());
        return null;
      }
    };
  }
}

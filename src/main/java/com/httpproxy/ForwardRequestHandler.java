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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/** 通用的请求处理器 (实现 HttpHandler 接口) */
@Slf4j
public class ForwardRequestHandler implements HttpHandler {
  private static final AtomicLong sessionIds = new AtomicLong(0);
  private static final LinkedBlockingQueue<Packet> packets = new LinkedBlockingQueue<>();
  private static final ReentrantLock lock = new ReentrantLock();
  private static final Map<Long, Function<Packet, Void>> tasks = new HashMap<>();

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
                final Packet receive = socketProtocols.receive();
                packets.put(receive);
              } catch (IOException | InterruptedException e) {
                log.error("Error receiving packet: {}", e.getMessage());
                throw new RuntimeException(e);
              }
            }, "receive-packet")
        .start();
    new Thread(
            () -> {
              try {
                lock.lock();
                try {
                  final Packet take = packets.take();
                  final var runnable = tasks.get(take.sessionId());
                  if (runnable != null) {
                    runnable.apply(take);
                  } else {
                    log.warn("No task found for sessionId: {}", take.sessionId());
                  }
                } finally {
                  lock.unlock();
                }
              } catch (InterruptedException e) {
                log.error("Error receiving packet: {}", e.getMessage());
                throw new RuntimeException(e);
              }
            }, "httpxProxy-receive-apply")
        .start();
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      final long sessionId = sessionIds.getAndIncrement();
      String requestMethod = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();
      log.info(
          "Received {} request for {} from {}", requestMethod, path, exchange.getRemoteAddress());

      byte[] request = HttpSerializer.serializeRequest(exchange);

      Packet packet = new Packet(sessionId, request);
      log.trace("Sending request data: {}", new String(request, StandardCharsets.UTF_8));
      socketProtocols.send(packet);

      lock.lock();
      try {
        tasks.put(
            sessionId,
            receive -> {
              try {
                log.trace(
                    "Received response data: {}",
                    receive != null ? new String(receive.data(), StandardCharsets.UTF_8) : "null");
                if (receive == null) {
                  exchange.sendResponseHeaders(500, -1);
                  return null;
                }

                HttpResponseRecord response = HttpSerializer.deserializeResponse(receive.data());

                // 设置响应头（过滤 hop-by-hop 头）
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

                // 获取响应体
                byte[] responseBody = response.body();
                int contentLength = responseBody == null ? 0 : responseBody.length;

                // 发送响应头
                exchange.sendResponseHeaders(response.statusCode(), contentLength);

                // 发送响应体
                if (responseBody != null) {
                  exchange.getResponseBody().write(responseBody);
                }
                return null;
              } catch (IOException e) {
                throw new RuntimeException(e);
              } finally {
                exchange.close();
              }
            });
      } finally {
        lock.unlock();
      }

    } finally {
      // 关闭交换 (JDK 17 建议显式关闭)
      exchange.close();
    }
  }
}

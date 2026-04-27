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
import lombok.extern.slf4j.Slf4j;

/** 通用的请求处理器 (实现 HttpHandler 接口) */
@Slf4j
public class ForwardRequestHandler implements HttpHandler {

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

  ForwardRequestHandler() {}

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      String requestMethod = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();
      log.info(
          "Received {} request for {} from {}", requestMethod, path, exchange.getRemoteAddress());

      SocketProtocol socketProtocols = Server.getSocketProtocols();

      byte[] request = HttpSerializer.serializeRequest(exchange);

      Packet packet = new Packet(request);
      log.trace("Sending request data: {}", new String(request, StandardCharsets.UTF_8));
      socketProtocols.lock();
      try {
        socketProtocols.send(packet);
        var receive = socketProtocols.receive();
        log.trace(
            "Received response data: {}",
            receive != null ? new String(receive.data(), StandardCharsets.UTF_8) : "null");
        if (receive == null) {
          exchange.sendResponseHeaders(500, -1);
          return;
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
      } finally {
        socketProtocols.unlock();
      }

    } finally {
      // 关闭交换 (JDK 17 建议显式关闭)
      exchange.close();
    }
  }
}

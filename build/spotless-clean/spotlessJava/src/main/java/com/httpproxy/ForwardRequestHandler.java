package com.httpproxy;

import static com.httpproxy.util.Consistant.formatter;

import com.httpproxy.pojo.HttpResponseRecord;
import com.httpproxy.pojo.Packet;
import com.httpproxy.util.HttpSerializer;
import com.httpproxy.util.SocketProtocol;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 通用的请求处理器 (实现 HttpHandler 接口) */
public class ForwardRequestHandler implements HttpHandler {

  ForwardRequestHandler() {}

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      String requestMethod = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();
      System.out.printf(
          "%s [INFO] Received %s request for %s from %s%n",
          LocalDateTime.now().format(formatter), requestMethod, path, exchange.getRemoteAddress());

      SocketProtocol socketProtocols = Server.getSocketProtocols();

      byte[] request = HttpSerializer.serializeRequest(exchange);

      Packet packet = new Packet(request);
      System.out.printf(
          "%s [DEBUG] Sending request %s to server...%n",
          LocalDateTime.now().format(formatter), packet);
      socketProtocols.send(packet);
      var receive = socketProtocols.receive();
      System.out.printf(
          "%s [DEBUG] Received response %s from server%n",
          LocalDateTime.now().format(formatter), receive);
      if (receive == null) {
        exchange.sendResponseHeaders(500, -1);
        return;
      }

      HttpResponseRecord response = HttpSerializer.deserializeResponse(receive.data());

      // 设置响应头
      if (response.headers() != null) {
        Headers responseHeaders = exchange.getResponseHeaders();
        for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
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
      // 关闭交换 (JDK 17 建议显式关闭)
      exchange.close();
    }
  }
}

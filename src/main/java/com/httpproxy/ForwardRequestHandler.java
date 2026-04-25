package com.httpproxy;

import static com.httpproxy.util.Consistant.formatter;

import com.httpproxy.pojo.Packet;
import com.httpproxy.util.HttpSerializer;
import com.httpproxy.util.SocketProtocol;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.LocalDateTime;

/** 通用的请求处理器 (实现 HttpHandler 接口) */
class ForwardRequestHandler implements HttpHandler {
  private final String protocol;

  ForwardRequestHandler(String protocol) {
    this.protocol = protocol;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String requestMethod = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    System.out.printf(
        "%s [INFO] [%s] Received %s request for %s from %s%n",
        LocalDateTime.now().format(formatter),
        protocol,
        requestMethod,
        path,
        exchange.getRemoteAddress());

    SocketProtocol socketProtocols = Server.getSocketProtocols();

    byte[] serialize = HttpSerializer.serializeRequest(exchange);
    Packet packet = new Packet(serialize);
    socketProtocols.send(packet);

    var receive = socketProtocols.receive();
    if (receive == null) {
      exchange.sendResponseHeaders(500, -1);
      return;
    }

    byte[] data = receive.data();

    // 关闭交换 (JDK 17 建议显式关闭)
    exchange.close();
  }
}

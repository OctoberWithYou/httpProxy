package com.httpproxy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyApplication {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      log.info(
          "Usage: java -jar http-proxy.jar [server [httpsPort tcpPort]|client proxyIp[:port] [schema host port]]");
      log.info("  server: 启动代理服务器");
      log.info("    httpsPort: HTTPS 代理端口 (默认 443)");
      log.info("    tcpPort: TCP 长连接端口 (默认 8443)");
      log.info("    IP白名单从 ip-whitelist.txt 读取（热加载，每10秒轮询）");
      log.info("  client: 启动客户端连接");
      log.info("    proxyIp[:port]: 代理服务器地址");
      log.info("    schema host port: 目标服务地址");
      return;
    }
    if (args[0].equals("server")) {
      if (args.length > 1) {
        HttpServerProxy.port = Integer.parseInt(args[1]);
        Server.port = Integer.parseInt(args[2]);
      }
      log.info("Starting server...");
      new Thread(
              () -> {
                try {
                  Server.start();
                } catch (Exception e) {
                  log.error("Server error", e);
                }
              })
          .start();
      HttpServerProxy.start();
      log.info("Server started.");
    } else if (args[0].equals("client")) {
      log.info("Starting client...");
      if (args.length > 2) {
        HttpClientProxy.schema = args[2];
        HttpClientProxy.targetHost = args[3];
        HttpClientProxy.targetPort = args[4];
      }
      Client.start(args[1]);
    }
  }
}

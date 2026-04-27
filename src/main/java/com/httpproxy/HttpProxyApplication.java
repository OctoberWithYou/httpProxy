package com.httpproxy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyApplication {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      log.info(
          "Usage: java -jar http-proxy.jar [server [httpsPort tcpPort]|client proxyIp[:port] [schema host port]]");
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

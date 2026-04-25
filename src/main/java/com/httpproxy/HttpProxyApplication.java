package com.httpproxy;

public class HttpProxyApplication {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println(
          "Usage: java -jar http-proxy.jar [server [httpsPort tcpPort]|client proxyIp[:port] [schema host port]]");
    }
    if (args[0].equals("server")) {
      if (args.length > 1) {
        HttpServerProxy.port = Integer.parseInt(args[1]);
        Server.port = Integer.parseInt(args[2]);
      }
      System.out.println("Starting server...");
      new Thread(
              () -> {
                try {
                  Server.start();
                } catch (Exception e) {
                  e.printStackTrace();
                }
              })
          .start();
      HttpServerProxy.start();
      System.out.println("Server started.");
    } else if (args[0].equals("client")) {
      System.out.println("Starting client...");
      if (args.length > 2) {
        HttpClientProxy.schema = args[2];
        HttpClientProxy.targetHost = args[3];
        HttpClientProxy.targetPort = args[4];
      }
      Client.start(args[1]);
    }
  }
}

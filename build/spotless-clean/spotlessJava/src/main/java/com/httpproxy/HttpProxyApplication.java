package com.httpproxy;

public class HttpProxyApplication {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("Usage: java -jar http-proxy.jar [server|client [schema host port]]");
    }
    if (args[0].equals("server")) {
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
      HttpClientProxy.schema = args[1];
      HttpClientProxy.targetHost = args[2];
      HttpClientProxy.targetPort = args[3];
      Client.start();
    }
  }
}

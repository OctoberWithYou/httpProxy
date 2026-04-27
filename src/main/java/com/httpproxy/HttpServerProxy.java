package com.httpproxy;

import com.sun.net.httpserver.*;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpServerProxy {

  private static final String KEYSTORE_PATH = "keystore.jks";
  private static final String KEYSTORE_PASSWORD = "changeit";
  public static int port = 443;

  public static void start() throws Exception {
    log.info("Starting HttpServerProxy...");

    // 1. 初始化 SSLContext
    var sslContext = initSSLContext(KEYSTORE_PATH, KEYSTORE_PASSWORD);

    // 2. 创建线程池用于处理并发请求
    ExecutorService threadPool = Executors.newCachedThreadPool();

    // 3. 启动 HTTPS 代理入口
    var httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
    httpsServer.setHttpsConfigurator(
        new HttpsConfigurator(sslContext) {
          @Override
          public void configure(HttpsParameters params) {
            try {
              var c = getSSLContext();
              var engine = c.createSSLEngine();
              params.setCipherSuites(engine.getEnabledCipherSuites());
              params.setProtocols(engine.getEnabledProtocols());
              params.setNeedClientAuth(false);
            } catch (Exception e) {
              throw new RuntimeException("Failed to configure HTTPS", e);
            }
          }
        });

    // 注册处理器：所有路径都交给 ForwardRequestHandler 处理
    httpsServer.createContext("/", new ForwardRequestHandler());
    httpsServer.setExecutor(threadPool);
    Single.waitTcpConnect();
    httpsServer.start();

    log.info("HTTPS Proxy started on port {}", port);

    // 可选：启动 HTTP 入口并重定向或同样处理
    // startHttpEntryPoint(HTTP_PORT, threadPool);
  }

  /** 初始化 SSLContext */
  private static SSLContext initSSLContext(String path, String pwd) throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS");
    try (var fis = new FileInputStream(path)) {
      ks.load(fis, pwd.toCharArray());
    }
    var kmf = javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, pwd.toCharArray());
    var sc = SSLContext.getInstance("TLS");
    sc.init(kmf.getKeyManagers(), null, null);
    return sc;
  }
}

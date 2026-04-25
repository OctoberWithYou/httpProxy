package com.httpproxy;

import static com.httpproxy.util.Consistant.formatter;

import com.sun.net.httpserver.*;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;

public class HttpServerProxy {

  private static final int HTTPS_PORT = 443;
  private static final String KEYSTORE_PATH = "keystore.jks";
  private static final String KEYSTORE_PASSWORD = "changeit";

  public static void start() throws Exception {
    System.out.printf(
        "%s [INFO] Starting HttpServerProxy...%n", LocalDateTime.now().format(formatter));

    // 1. 初始化 SSLContext
    var sslContext = initSSLContext(KEYSTORE_PATH, KEYSTORE_PASSWORD);

    // 2. 创建线程池用于处理并发请求
    ExecutorService threadPool = Executors.newCachedThreadPool();

    // 3. 启动 HTTPS 代理入口
    var httpsServer = HttpsServer.create(new InetSocketAddress(HTTPS_PORT), 0);
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

    System.out.printf(
        "%s [INFO] HTTPS Proxy started on port %d%n",
        LocalDateTime.now().format(formatter), HTTPS_PORT);

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

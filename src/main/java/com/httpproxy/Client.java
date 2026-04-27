package com.httpproxy;

import com.httpproxy.pojo.HttpResponseRecord;
import com.httpproxy.pojo.Packet;
import com.httpproxy.util.HttpSerializer;
import com.httpproxy.util.SocketProtocol;
import java.io.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import lombok.extern.slf4j.Slf4j;

/**
 * SSL HTTPS 客户端（使用 SSLSocket）
 *
 * <p>特点： - 使用阻塞式 SSLSocket（简单易用） - 单向认证（只验证服务器证书） - SSL 加解密由 SSLSocket 自动处理
 */
@Slf4j
public class Client {

  /**
   * 自签名证书使用说明：
   *
   * <p>1. 导出服务器证书： keytool -exportcert -alias httpProxyCert -keystore keystore.jks -file cert.cer
   * -storepass changeit
   *
   * <p>2. 创建客户端信任库并导入服务器证书： keytool -importcert -alias serverCert -file cert.cer -keystore
   * truststore.jks -storepass changeit
   *
   * <p>3. 将 truststore.jks 放在项目根目录或 resources 目录
   */
  public static void start(String s) throws Exception {
    String[] split = s.split(":");
    String serverHost;
    int serverPort = 8443;
    if (split.length == 2) {
      serverHost = split[0];
      serverPort = Integer.parseInt(split[1]);
    } else {
      serverHost = s;
    }
    var truststorePath = "truststore.jks";
    var truststorePassword = "changeit";

    log.info("Starting SSL Client (SSLSocket mode)");

    // 初始化 SSLContext（单向认证）
    var sslContext = initSSLContext(truststorePath, truststorePassword);

    // 创建 SSLSocketFactory
    var sslSocketFactory = sslContext.getSocketFactory();

    log.info("Connecting to {}:{}...", serverHost, serverPort);

    // 创建 SSLSocket 并连接
    try (var sslSocket = (SSLSocket) sslSocketFactory.createSocket(serverHost, serverPort)) {

      // 启动 SSL 握手
      sslSocket.startHandshake();

      log.info("SSL Handshake completed successfully!");

      // 获取会话信息
      var sslSession = sslSocket.getSession();
      log.info("Protocol: {}", sslSession.getProtocol());
      log.info("Cipher Suite: {}", sslSession.getCipherSuite());
      log.info("Server Principal: {}", sslSession.getPeerPrincipal());

      // 发送和接收数据
      SocketProtocol socketProtocol =
          new SocketProtocol(sslSocket.getInputStream(), sslSocket.getOutputStream());

      while (true) {
        socketProtocol.lock();
        try {
          Packet receive = socketProtocol.receive();
          if (receive == null) {
            continue;
          }
          log.debug("Received request {} from server", receive);

          var request = HttpSerializer.deserializeRequest(receive.data());
          HttpResponseRecord httpResponseRecord = HttpClientProxy.requestLocal(request);

          byte[] response = HttpSerializer.serializeResponse(httpResponseRecord);

          Packet packet = new Packet(response);
          log.debug("Sending response {} to server...", packet);
          socketProtocol.send(packet);
        } finally {
          socketProtocol.unlock();
        }
      }

    } catch (SSLHandshakeException e) {
      log.error("SSL Handshake Failed: {}", e.getMessage());
      log.info("\n可能的原因：");
      log.info("1. 服务器证书未导入信任库");
      log.info("2. 证书已过期或域名不匹配");
      log.info("\n解决方案：");
      log.info("- 确保证书已正确导入 truststore.jks");
      log.info("- 检查证书有效期和 CN/SAN 配置");
      throw e;
    } catch (IOException e) {
      log.error("Connection Error: {}", e.getMessage(), e);
      throw e;
    } finally {
      log.info("Connection closed.");
    }
  }

  /**
   * 初始化 SSL 上下文（单向认证）
   *
   * @param truststorePath 信任库路径
   * @param truststorePassword 信任库密码
   * @return SSLContext 实例
   * @throws Exception 初始化失败
   */
  private static SSLContext initSSLContext(String truststorePath, String truststorePassword)
      throws Exception {
    if (false) {
      // 加载信任库
      var trustStore = KeyStore.getInstance("JKS");
      try (var truststoreFile = new FileInputStream(truststorePath)) {
        trustStore.load(truststoreFile, truststorePassword.toCharArray());
      }

      // 初始化信任管理器
      var trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(trustStore);

      // 创建并初始化 SSLContext
      var sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

      return sslContext;
    } else {
      var trustManagers =
          new TrustManager[] {
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType) {}

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // 什么都不做，即表示信任任何证书
              }

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          };
      SSLContext sc = SSLContext.getInstance("TLS");
      sc.init(null, trustManagers, new SecureRandom());
      return sc;
    }
  }
}

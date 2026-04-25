package com.httpproxy;

import static com.httpproxy.util.Consistant.formatter;

import com.httpproxy.pojo.HttpResponseRecord;
import com.httpproxy.pojo.Packet;
import com.httpproxy.util.HttpSerializer;
import com.httpproxy.util.SocketProtocol;
import java.io.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import javax.net.ssl.*;

/**
 * SSL HTTPS 客户端（使用 SSLSocket）
 *
 * <p>特点： - 使用阻塞式 SSLSocket（简单易用） - 单向认证（只验证服务器证书） - SSL 加解密由 SSLSocket 自动处理
 */
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

    System.out.printf(
        "%s [INFO] Starting SSL Client (SSLSocket mode)%n", LocalDateTime.now().format(formatter));

    // 初始化 SSLContext（单向认证）
    var sslContext = initSSLContext(truststorePath, truststorePassword);

    // 创建 SSLSocketFactory
    var sslSocketFactory = sslContext.getSocketFactory();

    System.out.printf(
        "%s [INFO] Connecting to %s:%d...%n",
        LocalDateTime.now().format(formatter), serverHost, serverPort);

    // 创建 SSLSocket 并连接
    try (var sslSocket = (SSLSocket) sslSocketFactory.createSocket(serverHost, serverPort)) {

      // 启动 SSL 握手
      sslSocket.startHandshake();

      System.out.printf(
          "%s [INFO] SSL Handshake completed successfully!%n",
          LocalDateTime.now().format(formatter));

      // 获取会话信息
      var sslSession = sslSocket.getSession();
      System.out.printf(
          "%s [INFO] Protocol: %s%n",
          LocalDateTime.now().format(formatter), sslSession.getProtocol());
      System.out.printf(
          "%s [INFO] Cipher Suite: %s%n",
          LocalDateTime.now().format(formatter), sslSession.getCipherSuite());
      System.out.printf(
          "%s [INFO] Server Principal: %s%n",
          LocalDateTime.now().format(formatter), sslSession.getPeerPrincipal());

      // 发送和接收数据
      SocketProtocol socketProtocol =
          new SocketProtocol(sslSocket.getInputStream(), sslSocket.getOutputStream());

      while (true) {
        Packet receive = socketProtocol.receive();
        System.out.printf(
            "%s [DEBUG] Received response %s from server%n",
            LocalDateTime.now().format(formatter), receive);
        if (receive == null) {
          continue;
        }

        var request = HttpSerializer.deserializeRequest(receive.data());
        HttpResponseRecord httpResponseRecord = HttpClientProxy.requestLocal(request);

        byte[] response = HttpSerializer.serializeResponse(httpResponseRecord);

        Packet packet = new Packet(response);
        System.out.printf(
            "%s [DEBUG] Sending response %s to server...%n",
            LocalDateTime.now().format(formatter), packet);
        socketProtocol.send(packet);
      }

    } catch (SSLHandshakeException e) {
      System.out.printf(
          "%s [ERROR] SSL Handshake Failed: %s%n",
          LocalDateTime.now().format(formatter), e.getMessage());
      System.out.println("\n可能的原因：");
      System.out.println("1. 服务器证书未导入信任库");
      System.out.println("2. 证书已过期或域名不匹配");
      System.out.println("\n解决方案：");
      System.out.println("- 确保证书已正确导入 truststore.jks");
      System.out.println("- 检查证书有效期和 CN/SAN 配置");
      throw e;
    } catch (IOException e) {
      System.out.printf(
          "%s [ERROR] Connection Error: %s%n",
          LocalDateTime.now().format(formatter), e.getMessage());
      throw e;
    } finally {
      System.out.printf("%s [INFO] Connection closed.%n", LocalDateTime.now().format(formatter));
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

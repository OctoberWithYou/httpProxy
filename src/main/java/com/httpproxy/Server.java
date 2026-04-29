package com.httpproxy;

import com.httpproxy.util.SocketProtocol;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import javax.net.ssl.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server {
  private static SocketProtocol socketProtocols;
  public static int port = 8443;

  /** 心跳间隔（毫秒） */
  private static final int HEARTBEAT_INTERVAL = 30000;

  /**
   * SSL HTTPS代理服务器
   *
   * <p>自签名证书使用说明：
   *
   * <p>1. 生成自签名证书和密钥库（使用keytool）： keytool -genkeypair -alias httpProxyCert -keyalg RSA -keysize
   * 2048 -validity 365 \ -keystore keystore.jks -storepass changeit -keypass changeit \ -dname
   * "CN=localhost, OU=Development, O=HttpProxy, L=City, ST=State, C=CN"
   *
   * <p>2. 参数说明： - alias: 证书别名，可自定义 - keystore: 密钥库文件名 - storepass/keypass: 密钥库密码和密钥密码 - validity:
   * 证书有效期（天） - dname: 证书区分名，CN必须与服务器域名或IP匹配
   *
   * <p>3. 客户端信任自签名证书： - 导出证书：keytool -exportcert -alias httpProxyCert -keystore keystore.jks -file
   * cert.cer - 将证书导入客户端信任库或在浏览器中手动信任
   *
   * <p>4. 生产环境建议使用CA签发的正式证书
   */
  public static void start() throws Exception {
    var keystorePath = "keystore.jks"; // 密钥库文件路径
    var keystorePassword = "changeit"; // 密钥库密码

    // 初始化SSLContext
    var sslContext = initSSLContext(keystorePath, keystorePassword);

    // 创建SSLServerSocketFactory
    var sslServerSocketFactory = sslContext.getServerSocketFactory();

    // 创建并配置SSLServerSocket
    var socketThreadPool = Executors.newSingleThreadExecutor();
    try (var sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket()) {

      // 绑定端口
      sslServerSocket.bind(new InetSocketAddress(port));

      // 可选：只启用特定的TLS协议版本
      sslServerSocket.setEnabledProtocols(new String[] {"TLSv1.2", "TLSv1.3"});

      // 可选：只启用强加密套件
      // sslServerSocket.setEnabledCipherSuites(sslServerSocket.getSupportedCipherSuites());

      log.info("SSL Server started on port {}", port);

      // 接受客户端连接
      while (true) {
        var clientSocket = sslServerSocket.accept();

        // 启用 TCP Keep-Alive
        clientSocket.setKeepAlive(true);
        log.debug("TCP Keep-Alive enabled for client");

        socketThreadPool.submit(
            () -> {
              try (clientSocket) {
                // 获取SSL会话信息
                var sslSession = ((SSLSocket) clientSocket).getSession();
                String clientIp = clientSocket.getInetAddress().toString();
                String protocol = sslSession.getProtocol();
                String cipherSuite = sslSession.getCipherSuite();

                log.info(
                    "Client connected from {} | Protocol: {} | Cipher: {}",
                    clientIp,
                    protocol,
                    cipherSuite);

                socketProtocols =
                    new SocketProtocol(
                        clientSocket.getInputStream(), clientSocket.getOutputStream());

                // 启动心跳线程
                startHeartbeatThread(socketProtocols, clientIp);

                Single.notifyHttpProxyStart();

              } catch (IOException e) {
                log.warn("Client Error {}", e.getMessage(), e);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              } finally {
                log.info("Client disconnected");
              }
            });
      }
    } catch (Throwable e) {
      log.error("Server Error {}", e.getMessage(), e);
      throw e;
    } finally {
      socketThreadPool.shutdown();
    }
  }

  /** 启动心跳发送线程 */
  private static void startHeartbeatThread(SocketProtocol protocol, String clientIp) {
    Thread heartbeatThread =
        new Thread(
            () -> {
              try {
                while (true) {
                  Thread.sleep(HEARTBEAT_INTERVAL);
                  protocol.sendHeartbeat();
                  log.info("Heartbeat sent to {}", clientIp);
                }
              } catch (InterruptedException e) {
                log.info("Heartbeat thread interrupted for {}", clientIp);
              } catch (IOException e) {
                log.warn("Heartbeat failed for {}: {}", clientIp, e.getMessage());
              }
            },
            "heartbeat-" + clientIp);
    heartbeatThread.setDaemon(true);
    heartbeatThread.start();
  }

  /**
   * 初始化SSL上下文
   *
   * @param keystorePath 密钥库文件路径
   * @param keystorePassword 密钥库密码
   * @return SSLContext实例
   * @throws Exception 初始化失败时抛出异常
   */
  private static SSLContext initSSLContext(String keystorePath, String keystorePassword)
      throws Exception {
    // 加载密钥库
    var keyStore = KeyStore.getInstance("JKS");
    try (var keystoreFile = new FileInputStream(keystorePath)) {
      keyStore.load(keystoreFile, keystorePassword.toCharArray());
    }

    // 初始化密钥管理器
    var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, keystorePassword.toCharArray());

    // 创建并初始化SSLContext
    var sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

    return sslContext;
  }

  public static SocketProtocol getSocketProtocols() {
    return socketProtocols;
  }
}

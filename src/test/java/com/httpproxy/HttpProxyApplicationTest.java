package com.httpproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class HttpProxyApplicationTest {

  @Test
  void testHttpsPort443() throws Exception {
    String urlStr = "https://111.231.23.106:443";

    // 1. 创建无条件信任证书的 TrustManager (用于测试自签名证书)
    TrustManager[] trustAllCerts =
        new TrustManager[] {
          new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          }
        };

    // 2. 安装 TrustManager
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

    // 3. 忽略主机名验证 (防止 CN=localhost 但访问 IP 时报错)
    HostnameVerifier allHostsValid = (hostname, session) -> true;
    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

    // 4. 发起请求
    URL url = new URL(urlStr);
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);

    try {
      int responseCode = conn.getResponseCode();
      System.out.println("HTTPS Response Code from port 443: " + responseCode);

      // 只要不是网络错误，通常 200, 404, 500 等都代表端口是通的且 HTTPS 协议正常
      assertTrue(
          responseCode >= 200 && responseCode < 600, "Server responded with code: " + responseCode);

    } finally {
      conn.disconnect();
    }
  }

  @Test
  void testAccessBaidu() throws Exception {
    System.out.println("Starting test: Accessing https://www.baidu.com ...");

    // 1. 配置无条件信任证书 (防止自签名或证书链问题导致测试失败)
    TrustManager[] trustAllCerts =
        new TrustManager[] {
          new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          }
        };

    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());

    // 2. 创建使用了自定义 SSLContext 的 HttpClient
    InetSocketAddress proxyAddress = new InetSocketAddress("127.0.0.1", 8080);
    var proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, proxyAddress);
    HttpClient client =
        HttpClient.newBuilder()
            .sslContext(sc)
            .proxy(java.net.ProxySelector.of(proxyAddress))
            .build();

    // 3. 构建请求 (设置 User-Agent 模拟浏览器，防止被百度拦截)
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://www.baidu.com"))
            .header("User-Agent", "Mozilla/5.0 (Java HttpClient)")
            .GET()
            .build();

    // 4. 发送请求并获取响应
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // 5. 验证结果
    System.out.println("Status Code: " + response.statusCode());
    System.out.println("Response Body Length: " + response.body().length());

    assertEquals(200, response.statusCode(), "Baidu should return 200 OK");
  }
}

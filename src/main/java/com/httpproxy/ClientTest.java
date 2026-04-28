package com.httpproxy;

public class ClientTest {
  public static void main(String[] args) throws Exception {
    HttpProxyApplication.main(
        new String[] {"client", "111.231.32.106", "https", "ai-green.yasdb.com", "8444"});
  }
}

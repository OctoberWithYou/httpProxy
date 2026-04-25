package com.httpproxy.util;

import static com.httpproxy.util.Consistant.formatter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.httpproxy.pojo.HttpRequestRecord;
import com.httpproxy.pojo.HttpResponseRecord;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpSerializer {
  private static final Gson GSON = new GsonBuilder().create();

  /** 将 HttpExchange 序列化为 JSON 字节数组 */
  public static byte[] serializeRequest(HttpExchange exchange) throws IOException {
    Map<String, List<String>> headerMap = new HashMap<>();
    Headers requestHeaders = exchange.getRequestHeaders();

    // 2. 读取 Body
    byte[] body = exchange.getRequestBody().readAllBytes();

    // 3. 构造结构化对象
    HttpRequestRecord message =
        new HttpRequestRecord(
            exchange.getRequestMethod(),
            exchange.getRequestURI().toString(),
            "HTTP/1.1",
            new HashMap<>(requestHeaders),
            body);

    // 4. 转为 JSON 字符串并返回字节
    String json = GSON.toJson(message);

    System.out.printf(
        "%s [DEBUG] serialize request %s from server%n",
        LocalDateTime.now().format(formatter), json.length());

    return json.getBytes(StandardCharsets.UTF_8);
  }

  /** 将字节数组反序列化为 HttpMessage 对象 */
  public static HttpRequestRecord deserializeRequest(byte[] data) {
    String json = new String(data, StandardCharsets.UTF_8);
    System.out.printf(
        "%s [DEBUG] deserialize request %s from server%n",
        LocalDateTime.now().format(formatter), json.length());
    return GSON.fromJson(json, HttpRequestRecord.class);
  }

  public static byte[] serializeResponse(HttpResponseRecord httpResponseRecord) {
    String json = GSON.toJson(httpResponseRecord);
    System.out.printf(
        "%s [DEBUG] serialize response %s from server%n",
        LocalDateTime.now().format(formatter), json.length());
    return json.getBytes(StandardCharsets.UTF_8);
  }

  public static HttpResponseRecord deserializeResponse(byte[] data) {
    String json = new String(data, StandardCharsets.UTF_8);
    System.out.printf(
        "%s [DEBUG] deserialize response %s from server%n",
        LocalDateTime.now().format(formatter), json.length());
    return GSON.fromJson(json, HttpResponseRecord.class);
  }
}

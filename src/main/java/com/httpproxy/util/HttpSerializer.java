package com.httpproxy.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.httpproxy.pojo.HttpRequestRecord;
import com.httpproxy.pojo.HttpResponseRecord;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    return json.getBytes(StandardCharsets.UTF_8);
  }

  /** 将字节数组反序列化为 HttpMessage 对象 */
  public static HttpRequestRecord deserializeRequest(byte[] data) {
    return GSON.fromJson(new String(data, StandardCharsets.UTF_8), HttpRequestRecord.class);
  }

  public static byte[] serializeResponse(HttpResponseRecord httpResponseRecord) {
    String json = GSON.toJson(httpResponseRecord);
    return json.getBytes(StandardCharsets.UTF_8);
  }
}

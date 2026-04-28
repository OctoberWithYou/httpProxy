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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpSerializer {
  private static final Gson GSON = new GsonBuilder().create();

  /** 将 HttpExchange 序列化为 JSON 字节数组 */
  public static byte[] serializeRequest(HttpExchange exchange) throws IOException {
    Headers requestHeaders = exchange.getRequestHeaders();

    // 2. 读取 Body
    byte[] body = exchange.getRequestBody().readAllBytes();

    // 3. 构造结构化对象
    HttpRequestRecord message =
        new HttpRequestRecord(
            exchange.getRequestMethod(),
            exchange.getRequestURI().toString(),
            exchange.getProtocol(),
            new HashMap<>(requestHeaders),
            body);

    // 4. 转为 JSON 字符串并返回字节
    String json = GSON.toJson(message);

    log.debug("serialize request {} from server", json.length());

    return json.getBytes(StandardCharsets.UTF_8);
  }

  /** 将字节数组反序列化为 HttpMessage 对象 */
  public static HttpRequestRecord deserializeRequest(byte[] data) {
    String json = new String(data, StandardCharsets.UTF_8);
    log.debug("deserialize request length={} from server", json.length());
    HttpRequestRecord record = GSON.fromJson(json, HttpRequestRecord.class);
    log.debug(
        "deserialize request: method={}, path={}, headers={}, bodyLen={}",
        record.method(),
        record.path(),
        record.headers(),
        record.body() != null ? record.body().length : 0);
    return record;
  }

  public static byte[] serializeResponse(HttpResponseRecord httpResponseRecord) {
    String json = GSON.toJson(httpResponseRecord);
    log.debug("serialize response length={} from server", json.length());
    log.debug(
        "serialize response: statusCode={}, reasonPhrase={}, protocol={}, isSse={}, isSseEnd={}, headers={}, bodyLen={}",
        httpResponseRecord.statusCode(),
        httpResponseRecord.reasonPhrase(),
        httpResponseRecord.protocol(),
        httpResponseRecord.isSse(),
        httpResponseRecord.isSseEnd(),
        httpResponseRecord.headers(),
        httpResponseRecord.body() != null ? httpResponseRecord.body().length : 0);
    return json.getBytes(StandardCharsets.UTF_8);
  }

  public static HttpResponseRecord deserializeResponse(byte[] data) {
    String json = new String(data, StandardCharsets.UTF_8);
    log.debug("deserialize response length={} from server", json.length());
    HttpResponseRecord record = GSON.fromJson(json, HttpResponseRecord.class);
    log.debug(
        "deserialize response: statusCode={}, reasonPhrase={}, protocol={}, isSse={}, isSseEnd={}, headers={}, bodyLen={}",
        record.statusCode(),
        record.reasonPhrase(),
        record.protocol(),
        record.isSse(),
        record.isSseEnd(),
        record.headers(),
        record.body() != null ? record.body().length : 0);
    if (record.headers() != null) {
      log.debug("deserialize response headers detail: {}", record.headers());
    }
    if (record.body() != null && record.body().length < 500) {
      log.debug(
          "deserialize response body content: {}",
          new String(record.body(), StandardCharsets.UTF_8));
    }
    return record;
  }
}

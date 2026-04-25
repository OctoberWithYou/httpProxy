package com.httpproxy;

import com.httpproxy.pojo.HttpRequestRecord;
import com.httpproxy.pojo.HttpResponseRecord;

import static com.httpproxy.util.Consistant.formatter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpClientProxy {
  private static final HttpClient httpClient = HttpClient.newBuilder().build();

  public static HttpResponseRecord requestLocal(HttpRequestRecord httpRequestRecord) {
    try {
      HttpResponse<byte[]> response =
          httpClient.send(
              prepareRequest(httpRequestRecord), HttpResponse.BodyHandlers.ofByteArray());
      Map<String, List<String>> responseHeaders = new HashMap<>();
      response
          .headers()
          .map()
          .forEach(
              (name, values) -> {
                responseHeaders.put(name, new ArrayList<>(values));
              });

      return new HttpResponseRecord(
          response.statusCode(),
          getReasonPhrase(response.statusCode()), // 或映射 reason phrase
          response.version().name(),
          responseHeaders,
          response.body());
    } catch (Exception e) {
      System.err.printf(
          "%s [ERROR] Local request forwarding failed: %s%n",
          LocalDateTime.now().format(formatter), e.getMessage());
      e.printStackTrace();
      return new HttpResponseRecord(500, "", "", new HashMap<>(), null);
    }
  }

  private static HttpRequest prepareRequest(HttpRequestRecord httpRequestRecord) {
    HttpRequest.Builder builder = HttpRequest.newBuilder();
    String method = httpRequestRecord.method().toUpperCase();
    HttpRequest.BodyPublisher bodyPublisher =
        (httpRequestRecord.body() == null || httpRequestRecord.body().length == 0)
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(httpRequestRecord.body());
    switch (method) {
      case "GET" -> builder.method("GET", bodyPublisher);
      case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody()); // HEAD 不应有 body
      case "POST" -> builder.method("POST", bodyPublisher);
      case "PUT" -> builder.method("PUT", bodyPublisher);
      case "DELETE" -> builder.method("DELETE", bodyPublisher);
      case "CONNECT" -> builder.method("CONNECT", HttpRequest.BodyPublishers.noBody());
      case "OPTIONS" -> builder.method("OPTIONS", bodyPublisher);
      case "TRACE" -> builder.method("TRACE", HttpRequest.BodyPublishers.noBody());
      case "PATCH" -> builder.method("PATCH", bodyPublisher);
      default ->
          throw new IllegalArgumentException(
              "Unsupported HTTP method: " + httpRequestRecord.method());
    }
    builder.uri(URI.create(httpRequestRecord.path()));
    for (var header : httpRequestRecord.headers().entrySet()) {
      for (String v : header.getValue()) {
        builder.header(header.getKey(), v);
      }
    }
    return builder.build();
  }

  // 状态码映射 reason phrase
  private static String getReasonPhrase(int statusCode) {
    return switch (statusCode) {
      // 1xx 信息响应
      case 100 -> "Continue";
      case 101 -> "Switching Protocols";
      case 102 -> "Processing";

      // 2xx 成功
      case 200 -> "OK";
      case 201 -> "Created";
      case 202 -> "Accepted";
      case 203 -> "Non-Authoritative Information";
      case 204 -> "No Content";
      case 205 -> "Reset Content";
      case 206 -> "Partial Content";

      // 3xx 重定向
      case 300 -> "Multiple Choices";
      case 301 -> "Moved Permanently";
      case 302 -> "Found";
      case 303 -> "See Other";
      case 304 -> "Not Modified";
      case 307 -> "Temporary Redirect";
      case 308 -> "Permanent Redirect";

      // 4xx 客户端错误
      case 400 -> "Bad Request";
      case 401 -> "Unauthorized";
      case 402 -> "Payment Required";
      case 403 -> "Forbidden";
      case 404 -> "Not Found";
      case 405 -> "Method Not Allowed";
      case 406 -> "Not Acceptable";
      case 407 -> "Proxy Authentication Required";
      case 408 -> "Request Timeout";
      case 409 -> "Conflict";
      case 410 -> "Gone";
      case 411 -> "Length Required";
      case 412 -> "Precondition Failed";
      case 413 -> "Payload Too Large";
      case 414 -> "URI Too Long";
      case 415 -> "Unsupported Media Type";
      case 416 -> "Range Not Satisfiable";
      case 417 -> "Expectation Failed";
      case 429 -> "Too Many Requests";

      // 5xx 服务端错误
      case 500 -> "Internal Server Error";
      case 501 -> "Not Implemented";
      case 502 -> "Bad Gateway";
      case 503 -> "Service Unavailable";
      case 504 -> "Gateway Timeout";
      case 505 -> "HTTP Version Not Supported";

      default -> "";
    };
  }
}

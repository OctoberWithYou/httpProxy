package com.httpproxy;

import com.httpproxy.pojo.HttpRequestRecord;
import com.httpproxy.pojo.HttpResponseRecord;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpClientProxy {
  private static final Set<String> RESTRICTED_HEADERS =
      Set.of(
          "connection",
          "content-length",
          "expect",
          "host",
          "upgrade",
          "transfer-encoding",
          "proxy-authorization",
          "proxy-connection",
          "keep-alive",
          "te",
          "trailer");
  private static final HttpClient httpClient = HttpClient.newBuilder().build();
  public static String schema = "http";
  public static String targetHost = "127.0.0.1";
  public static String targetPort = "80";

  public static void requestLocal(
      HttpRequestRecord httpRequestRecord, Function<HttpResponseRecord, Void> callback) {
    try {
      HttpRequest request = prepareRequest(httpRequestRecord);

      // 同步请求
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

      Map<String, List<String>> responseHeaders = new HashMap<>();
      response
          .headers()
          .map()
          .forEach((name, values) -> responseHeaders.put(name, new ArrayList<>(values)));

      // 检测是否为 SSE 响应
      boolean isSse =
          responseHeaders.containsKey("Content-Type")
              && responseHeaders.get("Content-Type").stream()
                  .anyMatch(v -> v.contains("text/event-stream"));

      if (isSse) {
        // SSE 流开始
        callback.apply(
            HttpResponseRecord.sseStart(
                response.statusCode(),
                getReasonPhrase(response.statusCode()),
                response.version().name(),
                responseHeaders));

        // 读取 SSE 流 - 直接透传
        try (InputStream inputStream = response.body()) {
          byte[] buffer = new byte[1024];
          int bytesRead;
          while ((bytesRead = inputStream.read(buffer)) != -1) {
            byte[] chunk = Arrays.copyOf(buffer, bytesRead);
            callback.apply(HttpResponseRecord.sseEvent(chunk));
          }
        }

        // SSE 流结束
        callback.apply(HttpResponseRecord.sseEnd());
      } else {
        // 普通响应
        byte[] body = response.body().readAllBytes();
        callback.apply(
            HttpResponseRecord.normal(
                response.statusCode(),
                getReasonPhrase(response.statusCode()),
                response.version().name(),
                responseHeaders,
                body));
      }
    } catch (Exception e) {
      log.error("Local request forwarding failed: {}", e.getMessage(), e);
      callback.apply(HttpResponseRecord.normal(500, "", "", new HashMap<>(), null));
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
    String fullUrl =
        String.format("%s://%s:%s%s", schema, targetHost, targetPort, httpRequestRecord.path());
    log.debug("Forwarding request to {}", fullUrl);
    builder.uri(URI.create(fullUrl));
    for (var header : httpRequestRecord.headers().entrySet()) {
      for (String v : header.getValue()) {
        if (RESTRICTED_HEADERS.contains(header.getKey().toLowerCase())) {
          continue;
        }
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

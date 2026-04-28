package com.httpproxy.pojo;

import java.util.List;
import java.util.Map;

/**
 * HTTP 响应记录
 *
 * @param statusCode 状态码
 * @param reasonPhrase 原因短语
 * @param protocol 协议版本
 * @param headers 响应头
 * @param body 响应体
 * @param isSse 是否为 SSE 协议
 * @param isSseEnd 是否为 SSE 最后一个包
 */
public record HttpResponseRecord(
    int statusCode,
    String reasonPhrase,
    String protocol,
    Map<String, List<String>> headers,
    byte[] body,
    boolean isSse,
    boolean isSseEnd) {

  /** 普通响应 */
  public static HttpResponseRecord normal(
      int statusCode,
      String reasonPhrase,
      String protocol,
      Map<String, List<String>> headers,
      byte[] body) {
    return new HttpResponseRecord(statusCode, reasonPhrase, protocol, headers, body, false, false);
  }

  /** SSE 流开始 */
  public static HttpResponseRecord sseStart(
      int statusCode, String reasonPhrase, String protocol, Map<String, List<String>> headers) {
    return new HttpResponseRecord(statusCode, reasonPhrase, protocol, headers, null, true, false);
  }

  /** SSE 事件 */
  public static HttpResponseRecord sseEvent(byte[] body) {
    return new HttpResponseRecord(200, "OK", "HTTP/1.1", null, body, true, false);
  }

  /** SSE 流结束 */
  public static HttpResponseRecord sseEnd() {
    return new HttpResponseRecord(200, "OK", "HTTP/1.1", null, null, true, true);
  }
}

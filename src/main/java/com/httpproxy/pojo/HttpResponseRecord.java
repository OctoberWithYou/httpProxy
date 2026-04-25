package com.httpproxy.pojo;

import java.util.List;
import java.util.Map;

public record HttpResponseRecord(
    int statusCode,
    String reasonPhrase,
    String protocol,
    Map<String, List<String>> headers,
    byte[] body) {}

package com.httpproxy.pojo;

import java.util.List;
import java.util.Map;

public record HttpRequestRecord(
    String method, String path, String protocol, Map<String, List<String>> headers, byte[] body) {}

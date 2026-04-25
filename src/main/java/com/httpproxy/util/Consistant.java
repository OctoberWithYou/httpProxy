package com.httpproxy.util;

import java.time.format.DateTimeFormatter;

public class Consistant {
  public static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
}

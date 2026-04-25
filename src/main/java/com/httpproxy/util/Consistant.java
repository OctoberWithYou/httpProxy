package com.httpproxy.util;

import java.time.format.DateTimeFormatter;

public class Consistant {
  public static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm:ss.SSS");
}

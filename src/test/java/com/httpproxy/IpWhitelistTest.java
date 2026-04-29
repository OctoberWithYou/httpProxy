package com.httpproxy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IpWhitelistTest {

  @Test
  void testDisabled() {
    IpWhitelist.loadFromString("");
    assertFalse(IpWhitelist.isEnabled());
    assertTrue(IpWhitelist.isAllowed("121.15.186.83:53414"));
    assertTrue(IpWhitelist.isAllowed("any-ip"));
  }

  @Test
  void testSingleIp() {
    IpWhitelist.loadFromString("192.168.1.100");
    assertTrue(IpWhitelist.isEnabled());

    // 匹配
    assertTrue(IpWhitelist.isAllowed("/192.168.1.100:8080"));
    assertTrue(IpWhitelist.isAllowed("192.168.1.100/192.168.1.100:8080"));
    assertTrue(IpWhitelist.isAllowed("192.168.1.100"));

    // 不匹配
    assertFalse(IpWhitelist.isAllowed("/192.168.1.101:8080"));
    assertFalse(IpWhitelist.isAllowed("192.168.1.101/192.168.1.101:8080"));
    assertFalse(IpWhitelist.isAllowed("192.168.1.101"));
  }

  @Test
  void testMultipleIps() {
    IpWhitelist.loadFromString("192.168.1.100,10.0.0.50");
    assertTrue(IpWhitelist.isEnabled());

    // 匹配
    assertTrue(IpWhitelist.isAllowed("/192.168.1.100:8080"));
    assertTrue(IpWhitelist.isAllowed("/10.0.0.50:1234"));

    // 不匹配
    assertFalse(IpWhitelist.isAllowed("/192.168.1.101:8080"));
    assertFalse(IpWhitelist.isAllowed("/10.0.0.51:1234"));
  }

  @Test
  void testCidr() {
    IpWhitelist.loadFromString("192.168.1.0/24");
    assertTrue(IpWhitelist.isEnabled());

    // 在网段内
    assertTrue(IpWhitelist.isAllowed("/192.168.1.1:8080"));
    assertTrue(IpWhitelist.isAllowed("/192.168.1.100:8080"));
    assertTrue(IpWhitelist.isAllowed("/192.168.1.255:8080"));

    // 不在网段内
    assertFalse(IpWhitelist.isAllowed("/192.168.2.1:8080"));
    assertFalse(IpWhitelist.isAllowed("/10.0.0.1:8080"));
  }

  @Test
  void testIpAndCidr() {
    IpWhitelist.loadFromString("192.168.1.100,10.0.0.0/8");
    assertTrue(IpWhitelist.isEnabled());

    // 单个 IP 匹配
    assertTrue(IpWhitelist.isAllowed("/192.168.1.100:8080"));

    // CIDR 网段匹配
    assertTrue(IpWhitelist.isAllowed("/10.0.0.1:8080"));
    assertTrue(IpWhitelist.isAllowed("/10.255.255.255:8080"));

    // 不匹配
    assertFalse(IpWhitelist.isAllowed("/192.168.1.101:8080"));
    assertFalse(IpWhitelist.isAllowed("/11.0.0.1:8080"));
  }

  @Test
  void testActualFormat() {
    IpWhitelist.loadFromString("121.15.186.83");
    assertTrue(IpWhitelist.isEnabled());

    // 实际 exchange.getRemoteAddress().toString() 格式
    assertTrue(IpWhitelist.isAllowed("121.15.186.83/121.15.186.83:53414"));
    assertTrue(IpWhitelist.isAllowed("/121.15.186.83:53414"));

    // 不匹配
    assertFalse(IpWhitelist.isAllowed("121.15.186.84/121.15.186.84:53414"));
  }

  @Test
  void testIpv6() {
    IpWhitelist.loadFromString("::1");
    assertTrue(IpWhitelist.isEnabled());

    // IPv6 localhost
    assertTrue(IpWhitelist.isAllowed("/[::1]:8080"));
    assertTrue(IpWhitelist.isAllowed("::1"));
  }

  @Test
  void testCommentLines() {
    IpWhitelist.loadFromString("# 这是注释\n192.168.1.100\n# 另一个注释\n10.0.0.1");
    assertTrue(IpWhitelist.isEnabled());

    // 匹配
    assertTrue(IpWhitelist.isAllowed("/192.168.1.100:8080"));
    assertTrue(IpWhitelist.isAllowed("/10.0.0.1:8080"));

    // 不匹配
    assertFalse(IpWhitelist.isAllowed("/192.168.1.101:8080"));
  }
}

package com.httpproxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * IP 白名单管理类
 *
 * <p>支持单个 IP 和 CIDR 格式（如: 192.168.1.1,10.0.0.0/24）
 *
 * <p>支持热加载，每 10 秒轮询配置文件
 */
@Slf4j
public class IpWhitelist {

  /** 配置文件路径 */
  private static final String CONFIG_FILE = "ip-whitelist.txt";

  /** 轮询间隔（毫秒） */
  private static final int POLL_INTERVAL = 10000;

  /** 允许的 IP 列表 */
  private static volatile Set<String> allowedIpSet = new HashSet<>();

  /** 允许的 CIDR 网段列表 */
  private static volatile Set<CidrBlock> allowedCidrSet = new HashSet<>();

  /** 是否启用白名单 -- GETTER -- 是否启用白名单 */
  @Getter private static volatile boolean enabled = false;

  /** 上次配置内容（用于检测变化） */
  private static String lastConfigContent = null;

  /** 初始化 IP 白名单并启动热加载线程 */
  public static void init() {
    // 从配置文件加载
    loadFromFile();

    // 启动热加载线程
    startHotReloadThread();
  }

  /** 启动热加载线程 */
  private static void startHotReloadThread() {
    Thread reloadThread =
        new Thread(
            () -> {
              while (true) {
                try {
                  Thread.sleep(POLL_INTERVAL);
                  loadFromFile();
                } catch (InterruptedException e) {
                  log.info("Hot reload thread interrupted");
                  break;
                }
              }
            },
            "ip-whitelist-reload");
    reloadThread.setDaemon(true);
    reloadThread.start();
    log.info("IP whitelist hot reload thread started (interval: {}ms)", POLL_INTERVAL);
  }

  /** 从配置文件加载 */
  private static void loadFromFile() {
    Path path = Paths.get(CONFIG_FILE);
    if (!Files.exists(path)) {
      if (enabled) {
        log.debug("Config file not found: {}, keeping current whitelist", CONFIG_FILE);
      }
      return;
    }

    try {
      String content = Files.readString(path).trim();
      if (content.equals(lastConfigContent)) {
        // 配置未变化，跳过
        return;
      }

      lastConfigContent = content;
      log.info("Loading IP whitelist from file: {}", CONFIG_FILE);
      loadFromString(content);

    } catch (IOException e) {
      log.warn("Failed to read config file: {}", e.getMessage());
    }
  }

  /** 从字符串加载配置（供测试使用） */
  public static void loadFromString(String ips) {
    if (ips == null || ips.isBlank()) {
      enabled = false;
      log.info("IP whitelist disabled");
      return;
    }

    Set<String> newIpSet = new HashSet<>();
    Set<CidrBlock> newCidrSet = new HashSet<>();

    // 支持逗号分隔和换行分隔
    String[] lines = ips.split("[,\n]");
    for (String ip : lines) {
      ip = ip.trim();
      // 跳过空行和注释行（以 # 开头）
      if (ip.isEmpty() || ip.startsWith("#")) {
        continue;
      }
      if (ip.contains("/")) {
        // CIDR 格式
        newCidrSet.add(new CidrBlock(ip));
        log.info("Added CIDR block: {}", ip);
      } else {
        // 单个 IP
        newIpSet.add(ip);
        log.info("Added IP: {}", ip);
      }
    }

    allowedIpSet = newIpSet;
    allowedCidrSet = newCidrSet;
    enabled = !newIpSet.isEmpty() || !newCidrSet.isEmpty();
    if (enabled) {
      log.info(
          "IP whitelist updated: {} IPs, {} CIDR blocks",
          allowedIpSet.size(),
          allowedCidrSet.size());
    } else {
      log.info("IP whitelist disabled (no valid IPs configured)");
    }
  }

  /**
   * 检查 IP 是否在白名单中
   *
   * @param clientIp 客户端 IP 地址（格式如: /121.15.186.83:53414 或 121.15.186.83/121.15.186.83:53414）
   * @return true 如果 IP 在白名单中或白名单未启用
   */
  public static boolean isAllowed(String clientIp) {
    if (!enabled) {
      return true;
    }

    // 提取纯 IP 地址
    String ip = clientIp;

    // 如果包含 /，取最后一个 / 后面的部分
    if (ip.contains("/")) {
      ip = ip.substring(ip.lastIndexOf("/") + 1);
    }

    // 去除端口（IPv6 格式，如 [::1]:8080）
    if (ip.startsWith("[") && ip.contains("]:")) {
      ip = ip.substring(1, ip.indexOf("]:"));
    }
    // 去除端口（IPv4 格式）
    else if (!ip.startsWith("[") && ip.matches("^[\\d.]+:\\d+$")) {
      ip = ip.substring(0, ip.lastIndexOf(":"));
    }

    ip = ip.trim();

    // 检查单个 IP
    if (allowedIpSet.contains(ip)) {
      return true;
    }

    // 检查 CIDR 网段
    try {
      InetAddress addr = InetAddress.getByName(ip);
      for (CidrBlock cidr : allowedCidrSet) {
        if (cidr.contains(addr)) {
          return true;
        }
      }
    } catch (UnknownHostException e) {
      log.warn("Invalid client IP format: {} -> {}", clientIp, ip);
      return false;
    }

    return false;
  }

  /** 获取白名单配置摘要 */
  public static String getAllowedIps() {
    StringBuilder sb = new StringBuilder();
    sb.append(allowedIpSet);
    if (!allowedCidrSet.isEmpty()) {
      sb.append(", CIDR: ");
      for (CidrBlock cidr : allowedCidrSet) {
        sb.append(cidr).append(" ");
      }
    }
    return sb.toString();
  }

  /** CIDR 网段 */
  private static class CidrBlock {
    private final byte[] network;
    private final byte[] mask;
    private final String cidr;

    CidrBlock(String cidr) {
      this.cidr = cidr;
      String[] parts = cidr.split("/");
      try {
        InetAddress addr = InetAddress.getByName(parts[0]);
        this.network = addr.getAddress();
        int prefixLen = Integer.parseInt(parts[1]);
        this.mask = createMask(network.length, prefixLen);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid CIDR: " + cidr);
      }
    }

    private byte[] createMask(int addrLen, int prefixLen) {
      byte[] mask = new byte[addrLen];
      int fullBytes = prefixLen / 8;
      int partialBits = prefixLen % 8;

      for (int i = 0; i < fullBytes; i++) {
        mask[i] = (byte) 0xFF;
      }
      if (partialBits > 0 && fullBytes < addrLen) {
        mask[fullBytes] = (byte) (0xFF << (8 - partialBits));
      }
      return mask;
    }

    boolean contains(InetAddress addr) {
      byte[] bytes = addr.getAddress();
      if (bytes.length != network.length) {
        return false;
      }
      for (int i = 0; i < bytes.length; i++) {
        if ((bytes[i] & mask[i]) != (network[i] & mask[i])) {
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return cidr;
    }
  }
}

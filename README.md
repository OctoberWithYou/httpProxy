# HTTP Proxy

基于 Java 的 HTTPS 正向代理服务器，支持 SSL/TLS 加密、IP 白名单热加载、SSE 流式响应。

## 功能特性

- **HTTPS 正向代理**：代理客户端 HTTPS 请求到目标服务器
- **SSL/TLS 加密**：客户端与服务端通过 SSL 长连接通信，保证数据安全
- **IP 白名单**：支持单 IP 和 CIDR 格式，配置文件热加载（每 10 秒轮询）
- **SSE 协议支持**：完整支持 Server-Sent Events 流式响应
- **心跳机制**：TCP Keep-Alive + 应用层心跳（30 秒），防止连接断开
- **HTTP/2 兼容**：自动过滤 HTTP/2 伪头部，兼容 HTTP/1.1 客户端

## 架构说明

```
┌─────────────┐      SSL 长连接       ┌─────────────┐      HTTP/HTTPS      ┌─────────────┐
│   Client    │◄────────────────────►│    Server   │◄────────────────────►│  Target     │
│ (本地服务)   │     TCP 8443         │  (代理服务)  │     Port 443         │  (API服务)  │
└─────────────┘                       └─────────────┘                      └─────────────┘
```

**组件说明：**

- **Client**：部署在目标服务附近，接收代理请求并转发到本地服务
- **Server**：代理服务器，接收外部请求并通过 SSL 长连接转发给 Client
- **通信协议**：自定义 Packet 协议 `[8字节 sessionId][8字节 size][N字节 Data]`

## 快速开始

### 1. 生成 SSL 证书

```bash
# 生成自签名证书
keytool -genkeypair -alias httpProxyCert -keyalg RSA -keysize 2048 -validity 365 \
  -keystore keystore.jks -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=Development, O=HttpProxy, L=City, ST=State, C=CN"
```

### 2. 配置 IP 白名单

编辑 `ip-whitelist.txt`：

```
# IP 白名单配置文件
# 支持单 IP 和 CIDR 格式
# 修改后自动生效（每 10 秒轮询）

121.15.186.83
192.168.1.0/24
10.0.0.100
```

### 3. 启动服务

**服务端（代理服务器）：**

```bash
# 默认端口（HTTPS: 443, TCP: 8443）
java -jar http-proxy.jar server

# 指定端口
java -jar http-proxy.jar server 8444 8443
```

**客户端（连接代理）：**

```bash
# 连接代理服务器，转发到本地服务
java -jar http-proxy.jar client proxy-server:8443 http localhost 8080
```

### 4. 使用代理

配置客户端使用代理：

```bash
# Claude Code 配置
export HTTPS_PROXY="https://proxy-server:443"

# 或在配置文件中
ANTHROPIC_BASE_URL="https://proxy-server:443/apps/anthropic"
```

## 配置说明

### IP 白名单配置 (`ip-whitelist.txt`)

| 格式 | 示例 | 说明 |
|------|------|------|
| 单 IP | `192.168.1.100` | 只允许该 IP |
| CIDR | `192.168.1.0/24` | 允许整个网段 |
| IPv6 | `::1` | IPv6 地址 |

**特性：**
- 热加载：修改文件后最多 10 秒自动生效
- 注释：以 `#` 开头的行会被忽略
- 禁用：文件为空或不存在时允许所有 IP

### 端口说明

| 端口 | 默认值 | 说明 |
|------|--------|------|
| HTTPS 端口 | 443 | 代理入口，接收外部 HTTPS 请求 |
| TCP 端口 | 8443 | SSL 长连接端口，Client 连接此端口 |

## 项目结构

```
src/main/java/com/httpproxy/
├── HttpProxyApplication.java    # 启动入口
├── HttpServerProxy.java         # HTTPS 代理服务
├── Server.java                  # SSL TCP 服务端
├── Client.java                  # SSL TCP 客户端
├── ForwardRequestHandler.java   # 请求转发处理器
├── HttpClientProxy.java         # HTTP 客户端（请求本地服务）
├── IpWhitelist.java             # IP 白名单管理（热加载）
├── Single.java                  # 启动同步工具
├── pojo/
│   ├── Packet.java              # 数据包协议
│   ├── HttpRequestRecord.java   # HTTP 请求记录
│   └── HttpResponseRecord.java  # HTTP 响应记录（含 SSE）
└── util/
    ├── SocketProtocol.java      # Socket 协议实现
    ├── HttpSerializer.java      # HTTP 序列化
```

## 协议说明

### Packet 协议

```
+----------------+----------------+----------------+
|  sessionId     |     size       |     data       |
|   (8 bytes)    |   (8 bytes)    |   (N bytes)    |
+----------------+----------------+----------------+
```

- **sessionId**：请求会话 ID，用于路由响应
- **size**：数据长度
- **data**：JSON 序列化的 HTTP 请求/响应

### 心跳包

sessionId = -1 表示心跳包，用于保持连接活跃。

## 开发指南

### 构建项目

```bash
./gradlew build
```

### 运行测试

```bash
./gradlew test
```

### 生成证书

```bash
keytool -exportcert -alias httpProxyCert -keystore keystore.jks -file cert.cer
```

## 安全说明

- **SSL 加密**：所有代理通信通过 SSL/TLS 加密
- **IP 白名单**：防止未授权访问，配置热加载
- **快速拒绝**：IP 不匹配时立即返回 403，不读取请求体
- **TCP Keep-Alive**：操作系统级别的连接保活

## 日志配置

日志级别配置在 `src/main/resources/log4j2.xml`：

```xml
<Logger name="com.httpproxy" level="INFO" additivity="false">
```

**日志输出示例：**

```
2026-04-29 10:22:21.879 [pool-3-thread-1] INFO  Server - Client connected from /121.15.186.83
2026-04-29 10:22:26.522 [pool-2-thread-1] INFO  IpWhitelist - IP matched: 121.15.186.83
2026-04-29 10:52:21.702 [heartbeat-/121.15.186.83] INFO  Server - Heartbeat sent to /121.15.186.83
```

## 常见问题

### 1. 连接断开

检查心跳日志是否正常输出（每 30 秒一次）。如果没有，可能是防火墙或 NAT 超时。

### 2. IP 白名单不生效

检查 `ip-whitelist.txt` 格式，确保没有多余的空格或注释符号。

### 3. SSE 响应异常

确认 `Content-Type: text/event-stream` 头正确传递。

## License

MIT
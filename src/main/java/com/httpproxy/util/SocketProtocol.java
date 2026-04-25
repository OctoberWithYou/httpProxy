package com.httpproxy.util;

import com.httpproxy.pojo.Packet;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class SocketProtocol {

  private final InputStream inputStream;
  private final OutputStream outputStream;

  public SocketProtocol(InputStream inputStream, OutputStream outputStream) {
    this.inputStream = inputStream;
    this.outputStream = outputStream;
  }

  /** 发送数据包 协议格式：[8字节 Head (存储 size)][N字节 Data] */
  public synchronized void send(Packet packet) throws IOException {
    // 1. 确保 head 中存储了正确的 size
    byte[] head = packet.head();
    if (head == null || head.length != 8) {
      head = new byte[8];
    }

    // 将 size 写入 head (大端序)
    ByteBuffer.wrap(head).putLong(packet.size());

    // 2. 依次发送 head 和 data
    outputStream.write(head);
    if (packet.data() != null && packet.data().length > 0) {
      outputStream.write(packet.data());
    }
    outputStream.flush();
  }

  /** 接收数据包 先读 8 字节 head 获取 size，再读剩余的数据 */
  public synchronized Packet receive() throws IOException {
    // 1. 读取 8 字节头部
    byte[] head = new byte[8];
    int readBytes = inputStream.readNBytes(head, 0, 8);

    if (readBytes == -1 || readBytes == 0) {
      throw new IOException("Connection closed.");
    }

    // 如果连接关闭或数据不足，返回 null
    if (readBytes < 8) {
      return null;
    }

    // 2. 从 head 中解析总长度 size
    long size = ByteBuffer.wrap(head).getLong();

    // 3. 计算数据部分的长度 (总长度 - 头部长度)
    int dataLength = (int) (size - 8);
    if (dataLength < 0) dataLength = 0;

    // 4. 读取数据部分
    byte[] data = new byte[dataLength];
    if (dataLength > 0) {
      readBytes = inputStream.readNBytes(data, 0, dataLength);
      if (readBytes < dataLength) {
        throw new IOException("Incomplete packet data received.");
      }
    }

    return new Packet(size, head, data);
  }
}

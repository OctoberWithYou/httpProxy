package com.httpproxy.util;

import com.httpproxy.pojo.Packet;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

public class SocketProtocol {

  private final InputStream inputStream;
  private final OutputStream outputStream;
  private final ReentrantLock lock = new ReentrantLock();

  public SocketProtocol(InputStream inputStream, OutputStream outputStream) {
    this.inputStream = inputStream;
    this.outputStream = outputStream;
  }

  /** 获取锁，外部调用 send/receive 前必须先获取锁 */
  public void lock() {
    lock.lock();
  }

  /** 释放锁，send/receive 完成后必须释放锁 */
  public void unlock() {
    lock.unlock();
  }

  /** 发送数据包 协议格式：[8字节 Head (存储 size)][N字节 Data] */
  public void send(Packet packet) throws IOException {
    byte[] head = packet.head();
    if (head == null || head.length != 8) {
      head = new byte[8];
    }

    // 将 size 写入 head (大端序)
    ByteBuffer.wrap(head).putLong(packet.size());

    // 依次发送 head 和 data
    outputStream.write(head);
    if (packet.data() != null && packet.data().length > 0) {
      outputStream.write(packet.data());
    }
    outputStream.flush();
  }

  /** 接收数据包 先读 8 字节 head 获取 size，再读剩余的数据 */
  public Packet receive() throws IOException {
    // 读取 8 字节头部
    byte[] head = new byte[8];
    int readBytes = inputStream.readNBytes(head, 0, 8);

    if (readBytes == -1 || readBytes == 0) {
      throw new IOException("Connection closed.");
    }

    if (readBytes < 8) {
      return null;
    }

    // 从 head 中解析总长度 size
    long size = ByteBuffer.wrap(head).getLong();

    // 计算数据部分的长度 (总长度 - 头部长度)
    int dataLength = (int) (size - 8);
    if (dataLength < 0) dataLength = 0;

    // 读取数据部分
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

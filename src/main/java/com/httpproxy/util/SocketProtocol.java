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
  private final ReentrantLock inputStreamLock = new ReentrantLock();
  private final ReentrantLock outputStreamLock = new ReentrantLock();

  public SocketProtocol(InputStream inputStream, OutputStream outputStream) {
    this.inputStream = inputStream;
    this.outputStream = outputStream;
  }

  /** 发送数据包 协议格式：[8字节 sessionId][8字节 size][N字节 Data] */
  public void send(Packet packet) throws IOException {
    outputStreamLock.lock();
    try {
      // 发送 sessionId (8字节)
      outputStream.write(packet.sessionIdBytes());

      // 发送 size (8字节)
      outputStream.write(packet.sizeBytes());

      // 发送 data
      if (packet.data() != null && packet.data().length > 0) {
        outputStream.write(packet.data());
      }
      outputStream.flush();
    } finally {
      outputStreamLock.unlock();
    }
  }

  /** 接收数据包 先读 8 字节 sessionId，再读 8 字节 size，最后读 data */
  public synchronized Packet receive() throws IOException {
    inputStreamLock.lock();
    try {
      // 读取 8 字节 sessionId
      byte[] sessionIdBytes = new byte[8];
      int readBytes = inputStream.readNBytes(sessionIdBytes, 0, 8);

      if (readBytes == -1 || readBytes == 0) {
        throw new IOException("Connection closed.");
      }

      if (readBytes < 8) {
        return null;
      }

      long sessionId = ByteBuffer.wrap(sessionIdBytes).getLong();

      // 读取 8 字节 size
      byte[] sizeBytes = new byte[8];
      readBytes = inputStream.readNBytes(sizeBytes, 0, 8);
      if (readBytes < 8) {
        throw new IOException("Incomplete packet: missing size bytes.");
      }

      long dataLength = ByteBuffer.wrap(sizeBytes).getLong();
      if (dataLength < 0) dataLength = 0;

      // 读取数据部分
      byte[] data = new byte[(int) dataLength];
      if (dataLength > 0) {
        readBytes = inputStream.readNBytes(data, 0, (int) dataLength);
        if (readBytes < dataLength) {
          throw new IOException("Incomplete packet data received.");
        }
      }

      return new Packet(sessionId, sessionIdBytes, dataLength, sizeBytes, data);
    } finally {
      inputStreamLock.unlock();
    }
  }
}

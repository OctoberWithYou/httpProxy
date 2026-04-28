package com.httpproxy.pojo;

import java.nio.ByteBuffer;

/**
 * 数据包记录类，用于封装会话ID、数据大小及实际数据内容。
 *
 * @param sessionIdBytes 会话ID的字节数组表示
 * @param size 数据长度
 * @param sizeBytes 数据长度的字节数组表示
 * @param data 实际数据内容
 */
public record Packet(
    long sessionId, byte[] sessionIdBytes, long size, byte[] sizeBytes, byte[] data) {

  /**
   * 构造数据包，自动将会话ID和数据长度转换为字节数组。
   *
   * @param sessionId 会话ID
   * @param data 实际数据内容
   */
  public Packet(long sessionId, byte[] data) {
    this(
        sessionId,
        ByteBuffer.allocate(8).putLong(sessionId).array(),
        data.length,
        ByteBuffer.allocate(8).putLong(data.length).array(),
        data);
  }
}

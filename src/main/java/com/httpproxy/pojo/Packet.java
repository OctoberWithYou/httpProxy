package com.httpproxy.pojo;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 数据包
 *
 * @param size 包总长度，包含头部和数据
 * @param head 头部数据 8byte，表征包长度
 * @param data 数据
 */
public record Packet(long size, byte[] head, byte[] data) {

  public Packet(byte[] data) {
    this(data.length + 8, ByteBuffer.allocate(8).putLong(data.length + 8).array(), data);
  }

  @Override
  public String toString() {
    return "Packet{"
        + "size="
        + size
        + ", head="
        + Arrays.toString(head)
        + ", data="
        + Arrays.toString(data)
        + '}';
  }
}

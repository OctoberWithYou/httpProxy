package com.httpproxy;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Single {
  private static final ReentrantLock lock = new ReentrantLock();
  private static final Condition condition = lock.newCondition();

  public static void waitTcpConnect() throws InterruptedException {
    lock.lock();
    try {
      condition.await();
    } finally {
      lock.unlock();
    }
  }

  public static void notifyHttpProxyStart() throws InterruptedException {
    lock.lock();
    try {
      condition.signalAll();
      condition.await();
    } finally {
      lock.unlock();
    }
  }
}

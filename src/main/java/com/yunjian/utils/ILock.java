package com.yunjian.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec
     * @return
     */
    boolean trylock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}

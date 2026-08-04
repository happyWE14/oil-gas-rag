package com.wong.collector.infrastructure.external.client.support;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 简单的外部 API 速率限制器。
 */
@Slf4j
public class RateLimiter {

    private final long minIntervalMs;
    private final Semaphore concurrencyLimiter;
    private final Lock timeLock = new ReentrantLock();
    private volatile long lastRequestTime = 0L;

    public RateLimiter(long minIntervalMs, int maxConcurrency) {
        this.minIntervalMs = minIntervalMs;
        this.concurrencyLimiter = new Semaphore(Math.max(1, maxConcurrency));
    }

    public void acquire() {
        try {
            concurrencyLimiter.acquire();
            timeLock.lock();
            try {
                long now = System.currentTimeMillis();
                long sinceLast = now - lastRequestTime;
                if (sinceLast < minIntervalMs) {
                    Thread.sleep(minIntervalMs - sinceLast);
                }
                lastRequestTime = System.currentTimeMillis();
            } finally {
                timeLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rate limiter interrupted", e);
        }
    }

    public void release() {
        concurrencyLimiter.release();
    }

    public boolean tryAcquire(long timeoutMs) {
        try {
            if (concurrencyLimiter.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                timeLock.lock();
                try {
                    long now = System.currentTimeMillis();
                    long sinceLast = now - lastRequestTime;
                    if (sinceLast < minIntervalMs) {
                        Thread.sleep(minIntervalMs - sinceLast);
                    }
                    lastRequestTime = System.currentTimeMillis();
                    return true;
                } finally {
                    timeLock.unlock();
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

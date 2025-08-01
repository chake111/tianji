package com.tianji.promotion;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

/**
 * @author: chake
 * @create 2025/7/29 09:57
 * @ClassName: RedissonTest
 * @Package: com.tianji.promotion
 * @Description:
 * @Version 1.0
 */
@SpringBootTest
public class RedissonTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void test() throws InterruptedException {
        RLock lock = redissonClient.getLock("test");
        try {
            //  看门狗机制不能设置失效时间 采用默认失效时间 30s
            boolean isLock = lock.tryLock();
            if (isLock) {
                System.out.println("获取分布式锁成功");
            } else {
                System.out.println("获取分布式锁失败");
            }

        } finally {
            // 释放分布式锁
//            lock.unlock();
        }
    }
}

package com.hmdp.handler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
//@SpringBootTest
public class RedisIdGenerate {

    @Resource
    private  StringRedisTemplate stringRedisTemplate;

//    private ExecutorService threadPool = Executors.newFixedThreadPool(200);

//    @Test
//    public  void Test() {
//        Runnable runnable = () -> {
//            System.out.println(generateGlobalId("order"));
//        };
//
//        for (int i = 0; i < 200; i++) {
//            threadPool.execute(runnable);
//        }
//
//
//
//        System.out.println(generateGlobalId("order"));
//    }



    /**
     * 生成规则：64位 Long类型的整形8个字节
     * 前32位表示时间戳，后32位表示唯一的id，使用redis的increment 范围达42亿，
     * @param prefix
     * @return
     */
    public  Long generateGlobalId(String prefix) {
//        1.获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long endStamp = now.toEpochSecond(ZoneOffset.UTC);
        LocalDateTime localDateTime = LocalDateTime.of(2010, 1, 1, 0, 0, 0);
        long beginStamp = localDateTime.toEpochSecond(ZoneOffset.UTC);
        long stamp = endStamp - beginStamp;
//        2.redis调用increment
        String buildKey = prefix + ":" + now.getYear() + ":" + now.getMonth().getValue() + ":" + now.getDayOfMonth();
        Long incrementId = stringRedisTemplate.opsForValue().increment(buildKey);
//        3.位运算
         return (stamp << 32) | incrementId;
    }

}

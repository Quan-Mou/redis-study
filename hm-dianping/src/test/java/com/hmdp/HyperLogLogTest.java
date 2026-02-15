package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class HyperLogLogTest {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void test() {

//       存入100w个数据
        int[] s = new int[1000];
        System.out.println(s.length);
        for (int i = 0; i < 1000000; i++) {
            stringRedisTemplate.opsForHyperLogLog().add("info","信息" + i);
        }

    }

}

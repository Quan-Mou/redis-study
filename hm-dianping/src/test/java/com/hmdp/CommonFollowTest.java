package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class CommonFollowTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Test
    public void testCommonFollow() {
        /**
         * 将参数1和参数2的交集保存到参数3这个zset中，也就是说参数3存的是参数1和参数2的共同好友
         */
//        stringRedisTemplate.opsForZSet().intersectAndStore("user:follow:1009", "user:follow:1012", "common:likers:1009_1012");
        stringRedisTemplate.opsForZSet().intersectAndStore()
    }

}

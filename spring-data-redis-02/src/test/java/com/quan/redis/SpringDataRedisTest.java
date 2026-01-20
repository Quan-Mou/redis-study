package com.quan.redis;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quan.redis.pojo.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@SpringBootTest()
public class SpringDataRedisTest {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource
    StringRedisTemplate stringRedisTemplate;


    @Test
    public void testManualSerialize() throws JsonProcessingException {
        User user = new User("quanmou",99);

        ObjectMapper mapper = new ObjectMapper();
//      手动序列化为字符串
        String userStr = mapper.writeValueAsString(user);

        stringRedisTemplate.opsForValue().set("user",userStr);

        String str = stringRedisTemplate.opsForValue().get("user");

        // 手动反序列化为User对象
        User userObj = mapper.readValue(str, User.class);
        System.out.println(userObj);

        stringRedisTemplate.expire("user",30, TimeUnit.SECONDS);
    }

    @Test
    public void testStringRedisTemplate() {
        String age = stringRedisTemplate.opsForValue().get("age");
        if(age == null){
            System.out.println("没有这个age,正在新增age");
            stringRedisTemplate.opsForValue().set("age","199");
            System.out.println(stringRedisTemplate.opsForValue().get("age"));
        } else {
            System.out.println(age);
        }
    }

    @Test
    public void testStringUser() throws JsonProcessingException {
        User user = new User("权某", 999);
        ObjectMapper mapper = new ObjectMapper();
        String userStr = mapper.writeValueAsString(user); // 序列化为字符串

        User u = mapper.readValue(userStr, User.class); // 反序列化为对象
        redisTemplate.opsForValue().set("user", user);
    }

    @Test
    public void testHash() {
        redisTemplate.opsForHash().put("user:1","b","b1");
    }

    @Test
    public void testRedisTemplate(){
        redisTemplate.opsForValue().set("name","哈哈哈哈hhh");
        Object name1 = redisTemplate.opsForValue().get("name");
        System.out.println(name1);
    }


}

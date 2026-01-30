package com.hmdp;

import com.hmdp.utils.RedisReenTrantEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;


import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {



    @Resource
    private RedisTemplate<String,RedisReenTrantEntity> redisTemplate;;
    /**
     * 模拟一个redis的可重入锁,不考虑原子性
     */
    @Test
    public void realizeRedisReentrantLock() {
        String field = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        String key = "lock:seckill:" + 111;
        RedisReenTrantEntity redisReenTrantEntity = new RedisReenTrantEntity(value,0);
        Boolean b = redisTemplate.opsForHash().putIfAbsent(key, field, redisReenTrantEntity);
        redisTemplate.expire(key,60, TimeUnit.SECONDS);
        if(!b) {
            return;
        }
            try {
//            再次获取锁：
                RedisReenTrantEntity obj = (RedisReenTrantEntity)redisTemplate.opsForHash().get(key, field);
                try {
                    if(obj != null && obj.getValue().equals(value)) {
                        obj.setCount(obj.getCount()+1);
                        redisTemplate.opsForHash().put(key, field, obj);
                    }
                } finally {
                    obj.setCount(obj.getCount() -1);
                    redisTemplate.opsForHash().put(key, field,obj);
                }
            } finally {
                RedisReenTrantEntity result = (RedisReenTrantEntity)redisTemplate.opsForHash().get(key, field);
                if (result != null && result.getCount() == 0) {
//                    可以释放锁
                    redisTemplate.delete(key);
                }
            }
    }
}

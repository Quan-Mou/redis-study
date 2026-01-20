package com.quan.jedis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisPoolUtils {

    private static JedisPool jedisPool;

    static  {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(20); // 最大连接数
        jedisPoolConfig.setMaxIdle(3); // 最大空想连接数
        jedisPool = new JedisPool(jedisPoolConfig,"localhost", 6379,2000,"123456");
    }

    public static Jedis getJedis(){
        return jedisPool.getResource();
    }

    public static void closeJedisPool(){
        if(jedisPool != null){
            jedisPool.close();
        }
    }
}

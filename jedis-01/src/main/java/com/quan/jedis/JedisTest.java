package com.quan.jedis;

import redis.clients.jedis.Jedis;

public class JedisTest {

    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);

        jedis.auth("123456");

        long ids = jedis.lpush("ids", "123", "34210", "212");

        jedis.setex("name",60,"何其自性，本自清净~");

        String name = jedis.get("name");

        System.out.println(name);

        jedis.expire("ids",30);
        System.out.println(ids);
    }

}

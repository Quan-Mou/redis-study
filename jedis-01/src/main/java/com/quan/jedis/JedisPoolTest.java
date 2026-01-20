package com.quan.jedis;


import redis.clients.jedis.Jedis;

public class JedisPoolTest {

    public static void main(String[] args) {
        try(Jedis jedis = JedisPoolUtils.getJedis()) {
            String name = jedis.get("name");
            if(name == null) {
                jedis.set("name","何其自性，本自具足~");
            }
            System.out.println(name);
        } // jedis会自动调用close，归还到连接池中
    }
}

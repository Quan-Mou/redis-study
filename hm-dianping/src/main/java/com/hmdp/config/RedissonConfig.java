package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    // 如果有密码
    @Value("${spring.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单机模式（根据你的部署方式调整）
        String address = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer()
              .setAddress(address)
              .setPassword("".equals(password) ? null : password);

        return Redisson.create(config);
    }
}
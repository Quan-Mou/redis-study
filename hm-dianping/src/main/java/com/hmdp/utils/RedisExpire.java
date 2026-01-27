package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisExpire<T> {
    private T Data;
    private LocalDateTime expireTime;
}

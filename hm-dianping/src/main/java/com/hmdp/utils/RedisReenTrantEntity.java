package com.hmdp.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class RedisReenTrantEntity {

    private String value;
    private Integer count;
}

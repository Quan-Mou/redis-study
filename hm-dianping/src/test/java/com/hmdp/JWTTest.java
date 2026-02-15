package com.hmdp;

import cn.hutool.core.date.DateUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.HashMap;

@SpringBootTest
public class JWTTest {

    @Value("${JWTSecret}")
    private String jwtSecret;

    @Test
    public void test() {
        HashMap<String, Long> map = new HashMap<>();
        map.put("id", 1L);
        String token = JWT.create()
                .addPayloads(map)
                .setExpiresAt(DateUtil.offsetDay(new Date(), 7))
                .setSigner(JWTSignerUtil.hs256(jwtSecret.getBytes()))
                .sign();

        System.out.println(token);

        JWT jwt = JWTUtil.parseToken(token);
        System.out.println(jwt.getPayload("id"));
        if (jwt.verify(JWTSignerUtil.hs256(jwtSecret.getBytes()))) {
            System.out.println("success");
        }
    }


}

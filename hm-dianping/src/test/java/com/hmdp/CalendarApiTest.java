package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
public class CalendarApiTest {


    @Test
    public void test() {
        LocalDateTime now = LocalDateTime.now();
//      获取当前月的第几天
        System.out.println(now.getDayOfMonth());
        System.out.println(now.getYear());
        System.out.println(now.getMonth());
        System.out.println(now.getHour());
        System.out.println(now.getMinute());
//        获取当前年的第几天
        System.out.println(now.getDayOfYear());
//        获取当前周的第几天
        System.out.println(now.getDayOfWeek().getValue());
    }


}

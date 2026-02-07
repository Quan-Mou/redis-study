package com.hmdp;

import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import javax.annotation.Resource;
import java.util.Set;
import java.util.stream.Collectors;

@SpringBootTest
public class CommonFollowTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Test
    public void testCommonFollow() {
        /**
         * 将参数1和参数2的交集保存到参数3这个zset中，也就是说参数3存的是参数1和参数2的共同好友
         */
//        stringRedisTemplate.opsForZSet().intersectAndStore("user:follow:1009", "user:follow:1012", "common:likers:1009_1012");
//        stringRedisTemplate.opsForZSet().intersectAndStore()
    }


    @Test
    public void testIsZsetExist() {
        Long size = stringRedisTemplate.opsForZSet().size(RedisConstants.USER_FOLLOW_KEY + 10);
        System.out.println(size);
    }


    @Test
    public void test() {
        long lastId = 0;
//        stringRedisTemplate.opsForZSet()
//                .reverseRangeByScoreWithScores(
//                        RedisConstants.USER_LETTERBOX_KEY + 1012,
//                        lastId,
//                        Double.parseDouble(String.valueOf(System.currentTimeMillis())),
//                         0, 3
//                ).stream().forEach(item ->{
//                    System.out.println(item.getValue());
//                    System.out.println(item.getScore());
//                });

        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(
                        RedisConstants.USER_LETTERBOX_KEY + 1012,
                        0,

                        1770219399282L, 0, 3
                );
        System.out.println(typedTuples.size());

    }


    @Test
    public void testZset() {
        DataType type1 = stringRedisTemplate.type("user:letterBox:" + 1);
        DataType type2 = stringRedisTemplate.type("user:follow:1009");

        if (type1.name().equals("none")) {
            System.out.println(type1);
        } else  {
            System.out.println(type1.name());
            System.out.println(type1.code());
        }
    }

}

package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@SpringBootTest
public class LoadShopGeoTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Test
    public void loadShop() {
        List<Shop> list = shopService.list();

//       redisKey安装每个商户的type分组：
//        shop:geo:typeId

//        1.获取所有商户信息，商户信息存入map中,map的value是一个集合
//        遍历获取商户，取到它的typeId
//        先判断这个typeId存不存在map中：
//        如果存在，直接把这个商户添加map的value中的list
//        如果不存在，说明是第一次添加，需要创建这个key
        Map<Long,List<Shop>> map = new HashMap<>();
        list.forEach(shop -> {
            Long typeId = shop.getTypeId();
            if(map.containsKey(typeId)) {
                map.get(typeId).add(shop);

            } else {
                List<Shop> shopList = new ArrayList<>();
                shopList.add(shop);
                map.put(typeId, shopList);
            }
        });

        map.forEach((k,v)->{
            v.forEach(shop->{
                Point point = new Point(shop.getX(), shop.getY());
                stringRedisTemplate.opsForGeo()
                        .add("shops:geo:" + k, point, shop.getId().toString());
            });
        });
    }


    /**
     * 签到测试
     */
    @Test
    public void testBitMap() {
//        1000000000000100
//        10000000000001
        String key = stringRedisTemplate.opsForValue().get("sign:1012:2026_2");

        int day = LocalDateTime.now().getDayOfMonth();
        System.out.println(day);
        List<Long> a = stringRedisTemplate.execute(
                (RedisCallback<List<Long>>) connection -> {
                    return connection.bitField(
                            "sign:1012:2026_2".getBytes(StandardCharsets.UTF_8),
                            BitFieldSubCommands.create()
                                    .get(BitFieldSubCommands.BitFieldType.unsigned(day )).valueAt(0)
                    );
                }
        );
        String byteString = Long.toBinaryString(a.get(0));
        int length = byteString.length();
        int count  = 0;
        for (int i = length -1; i>=0 ; i--) {
            char byteV = byteString.charAt(i);
            if(byteV == '1') {
                count++;
            }
            if(byteV == '0') {
                break;
            }
        }
//        10000000000001
        System.out.println("连续签到：" + count);

    }



}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisExpire;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    private ExecutorService threadPool = Executors.newFixedThreadPool(5);



    @Override
    public Result queryShopByIdAddCache(Long id) {
        return queryShopByIdWithLogicExpire(id);
    }

    /**
     * 解决缓存击穿-逻辑过期方式
     * @param id
     * @return
     */
    public Result queryShopByIdWithLogicExpire(Long id)  {
//        1.查询redis，如果存在，直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if(StringUtils.hasText(shopStr)) { // 缓存中存在数据
            RedisExpire obj = JSONUtil.toBean(shopStr, RedisExpire.class);
            LocalDateTime expireTime = obj.getExpireTime();
            if(LocalDateTime.now().isAfter(expireTime)) { // 过期
//                缓存重建,创建一个新的线程异步操作
                String cacheValue = RandomUtil.randomString(10);
                Boolean isReloadCache = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, cacheValue, 10, TimeUnit.SECONDS);
                if(!Boolean.TRUE.equals(isReloadCache)) { // 获取锁失败
                    return Result.ok(obj.getData()); // 直接返回旧数据
                }

//              获取锁成功，多开一个线程来进行重建缓存
                    threadPool.submit(() -> {
                        try {
                            Shop shop = this.getById(id);
                            RedisExpire<Shop> shopRedisExpire = new RedisExpire<>();
                            shopRedisExpire.setExpireTime(LocalDateTime.now().plusSeconds(10));
                            shopRedisExpire.setData(shop);
                            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+ id, JSONUtil.toJsonStr(shopRedisExpire));
                        } finally {
                            //                    释放锁
                            stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY+ id);
                        }
                    });
//              先返回旧数据，允许暂时的不一致
                String jsonStr = JSONUtil.toJsonStr(obj.getData());
                return Result.ok(JSONUtil.toBean(jsonStr,Shop.class));
            }

            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return Result.ok(shop);
        }

        if(shopStr == null) { //       命中缓存穿透
            Shop shop = this.getById(id);
            if(shop != null) {
                RedisExpire redisExpire = new RedisExpire();
                redisExpire.setExpireTime(LocalDateTime.now().plusSeconds(10));
                redisExpire.setData(shop);
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+ id, JSONUtil.toJsonStr(redisExpire));
                return Result.ok(shop);
            }
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+ id, "");
            return Result.fail("该店铺不存在！");
        }

//      走到这里说明缓存中的是""
        return Result.fail("该店铺不存在！");
    }



    /**
     * 解决缓存击穿-互斥锁方式
     * @param id
     * @return
     */
    public Result queryShopByIdWithBreakDown(Long id) throws InterruptedException {
//        1.查询redis，如果存在，直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if(StringUtils.hasText(shopStr)) {
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return Result.ok(shop);
        }

//       命中缓存穿透
        if(Objects.equals(shopStr, "")) {
            return Result.fail("该店铺不存在！");
        }
//        2.不存在先查询DB，然后缓存一个空值
        Shop shop = this.getById(id);
        if(shop == null) { // 穿透
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",10, TimeUnit.MINUTES);
            return Result.fail("该店铺不存在！");
        }
//      缓存击穿操作
        String lockValue = RandomUtil.randomString(10);
        try {
            //      1.获取互斥锁
            Boolean isReloadCache = stringRedisTemplate.opsForValue()
                    .setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, lockValue,30,TimeUnit.SECONDS);

            if(Boolean.FALSE.equals(isReloadCache)) { // 加锁失败  !isReloadCache、Boolean.FALSE.equals(isReloadCache)
                Thread.sleep(50);
                return queryShopByIdWithBreakDown(id);
            }
            // 双重检查
            String cache = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if(!Objects.isNull(cache)) {
                return Result.ok(JSONUtil.toBean(cache, Shop.class));
            }
//      2.重建缓存
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop));//
        } finally {
//      3.释放锁
//            这里的get+del不是一个原子操作！
            String lockV = stringRedisTemplate.opsForValue().get(RedisConstants.LOCK_SHOP_KEY + id);
            if(lockV != null && lockV.equals(lockValue)) {
                stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY + id);
            } else { // 不是当前线程的锁Value
                log.error("错误！");
            }
        }
        return Result.ok(shop);
    }




    /**
     * 解决缓存穿透问题，缓存一个空值
     * @param id
     * @return
     */
    public Result queryShopByIdWithThrough(Long id) {
//        1.查询redis，如果存在，直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if(StringUtils.hasText(shopStr)) {
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return Result.ok(shop);
        }

//       命中缓存穿透
        if(Objects.equals(shopStr, "")) {
            return Result.fail("该店铺不存在！");
        }
//        2.不存在先查询DB，然后缓存一个空值
        Shop shop = this.getById(id);
        if(shop == null) {
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",30, TimeUnit.MINUTES);
            return Result.fail("该店铺不存在！");
        }
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }


    @Override
    public Result updateByIdAsCache(Shop shop) {
//        1.操作数据库
        this.updateById(shop);
//        2.删除缓存（如果是高并发的场景下还需要进行一次延迟删缓存）
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        return Result.ok(shop);
    }
}

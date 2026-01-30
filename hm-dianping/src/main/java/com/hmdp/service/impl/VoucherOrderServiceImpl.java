package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.handler.RedisIdGenerate;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdGenerate redisIdGenerate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;



    @Override
    public Result seckillVoucher(Long voucherId) {
//        1.查询秒杀券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if(beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("还未到开始抢购时间");
        }

        LocalDateTime endTime = seckillVoucher.getEndTime();
        if(endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("抢购已经结束");
        }
//        2.判断库存是否充足
        if (seckillVoucher.getStock() <= 0) {
            return Result.fail("库存不足！");
        }

        Long orderId = null;
        Long userId = UserHolder.getUser().getId();
        String uuid = UUID.randomUUID().toString();
//        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(
//                RedisConstants.LOCK_SECKILL_KEY + userId + ":" + voucherId, uuid,
//                RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
//      获取可重入锁
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_SECKILL_KEY + userId + ":" + voucherId);
        try {
//           尝试获取锁（等待时间，锁自动释放时间，时间单位）
            boolean isLocked = lock.tryLock(3, 15, TimeUnit.SECONDS);
            if(!isLocked) {
                return Result.fail("不能重复下单！");
            }

            try {
                QueryWrapper<VoucherOrder> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("user_id",UserHolder.getUser().getId()).eq("voucher_id",voucherId);
//            select x from voucher_order where user_id = ? and voucher_id = ?
                VoucherOrder iSvoucherOrder = this.getOne(queryWrapper);
                if(iSvoucherOrder != null) {
                    return Result.fail("该优惠卷只能抢购一次！");
                }
                //        3.扣减库存  update xxx set stock = stock - 1 where stock > 0 and voucher_id = ?
                boolean isSuccess = seckillVoucherService.update()
                        .setSql("stock = stock - 1")
                        .gt("stock",0)
                        .eq("voucher_id", voucherId).update();

                if(!isSuccess) {
                    return Result.fail("库存不足！");
                }

//        4.生成订单
                orderId = redisIdGenerate.generateGlobalId("order");
                VoucherOrder voucherOrder = new VoucherOrder();
                voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
                voucherOrder.setCreateTime(LocalDateTime.now());
                voucherOrder.setUserId(UserHolder.getUser().getId());
                voucherOrder.setId(orderId);
                this.save(voucherOrder);
            } finally {
                /**
                 * 解决了误删锁的问题，但是下面两个命令 先get在del这是两个命令不具备原子性，所有还是可能会出现问题
                 * 解决办法：使用Lua脚本保证原子性
                 */
//            String uid = stringRedisTemplate.opsForValue().get(RedisConstants.LOCK_SECKILL_KEY + userId + ":" + voucherId);
//            if(uuid.equals(uid)) {
//                stringRedisTemplate.delete(RedisConstants.LOCK_SECKILL_KEY + userId + ":" + voucherId);
//            }
                /**
                 * 使用lua脚本，解决了释放锁的原子问题，但是如果锁自动过期了，但是业务代码还没执行完，还是会会出现并发数据不一致问题
                 * 解决办法：使用 redisson（watchDog看门狗，锁自动续期）
                 */
//            String RELEASE_LOCK_LUA =
//                    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
//                            "    return redis.call('DEL', KEYS[1]) " +
//                            "else " +
//                            "    return 0 " +
//                            "end";
//            RedisScript<Long> redisScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA);
//
//            stringRedisTemplate.execute(
//                    redisScript,
//                    Collections.singletonList(RedisConstants.LOCK_SECKILL_KEY + userId + ":" + voucherId),
//                    uuid);

                lock.unlock();

            }

        } catch (InterruptedException e) {
            return Result.fail("操作被中断，请重试");
        }

//        5.返回订单id
        return Result.ok(orderId);
    }
}

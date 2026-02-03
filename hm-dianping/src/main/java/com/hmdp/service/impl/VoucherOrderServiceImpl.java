package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.handler.RedisIdGenerate;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdGenerate redisIdGenerate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;


    private static  DefaultRedisScript<Long> redisScript;
    static  {
        redisScript = new DefaultRedisScript<>();

        String script =
                "                local voucherId = KEYS[1]\n" +
                        "                local userId = ARGV[1]\n" +
                        "\n" +
                        "                        -- 1. 判断库存\n" +
                        "                local stock = tonumber(redis.call('GET', 'seckill:stock:' .. voucherId))\n" +
                        "                if stock == nil or stock <= 0 then\n" +
                        "                return 1\n" +
                        "                end\n" +
                        "\n" +
                        "                        -- 2. 判断是否已下单\n" +
                        "                if redis.call('SISMEMBER', 'seckill:order:' .. voucherId, userId) == 1 then\n" +
                        "                return 2\n" +
                        "                end\n" +
                        "\n" +
                        "                        -- 3. 扣库存 + 记录用户\n" +
                        "                redis.call('DECR', 'seckill:stock:' .. voucherId)\n" +
                        "                redis.call('SADD', 'seckill:order:' .. voucherId, userId)\n" +
                        "                return 0";

        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);

    }


    private static ExecutorService voucherOrderWorker = Executors.newSingleThreadExecutor();

    private static BlockingQueue<VoucherOrder> voucherOrderTaskQueue = new LinkedBlockingQueue<>(300);

    private VoucherOrderServiceImpl proxy;
    private Long userId;
    private Long curVoucherId;


    @PostConstruct
    private void  init() {
        voucherOrderWorker.execute(() -> {
            while (true) {
                try {
//                    VoucherOrder order = voucherOrderTaskQueue.take();
                    String result = stringRedisTemplate.opsForList()
                            .rightPop(RedisConstants.SECKILL_ORDER_KEY+":queue", 0, TimeUnit.SECONDS);
                    log.info("消息：{}",result);
                    if (result != null) {
//                      这个proxy一定要放在异步执行之外获取
                        proxy.createVoucherOrder(JSONUtil.toBean(result, VoucherOrder.class));
                    }

                } catch (Exception e) {
                    log.info("创建订单出现异常:{}",e.getMessage());
                }
            }
        });
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        RLock lock = redissonClient.getLock(RedisConstants.LOCK_SECKILL_KEY + voucherOrder.getUserId());
        boolean isLocked = lock.tryLock();
        if(!isLocked){
            log.info("不允许重复下单");
            return;
        }
        try {
            //                   1. mysql 扣减库存
            //        3.扣减库存  update xxx set stock = stock - 1 where stock > 0 and voucher_id = ?
            boolean isSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .gt("stock",0)
                    .eq("voucher_id", voucherOrder.getVoucherId()).update();
//            log.info("手动抛异常，测试事务有没有生效");
//            String a = null;
//            a.toString();

//      2.添加订单数据
//        手动获取代理对象
            proxy.save(voucherOrder);
        } finally {
            lock.unlock();
        }
    }


    @Transactional
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
        userId = UserHolder.getUser().getId();
        curVoucherId = seckillVoucher.getVoucherId();

                Long value = stringRedisTemplate.execute(redisScript, Collections.singletonList(voucherId.toString()), userId.toString());

                if(value == null) {
                    return Result.fail("做啥呀，先去添加秒杀卷！");
                }
                int result = value.intValue();
                if(result == 1) {
                    return Result.fail("库存不足！");
                }

                if(result == 2) {
                    return Result.fail("不能重复下单！");
                }
//        4.生成订单
                orderId = redisIdGenerate.generateGlobalId("order");
                VoucherOrder voucherOrder = new VoucherOrder();
                voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
                voucherOrder.setCreateTime(LocalDateTime.now());
                voucherOrder.setUserId(UserHolder.getUser().getId());
                voucherOrder.setId(orderId);
//              在主线程中获取当前代理对象, 由于是spring的事务是放在threadLocal中，下面的是多线程，事务会失效
                proxy = (VoucherOrderServiceImpl)AopContext.currentProxy();
//                voucherOrderTaskQueue.add(voucherOrder);
//              放入到redis中list容器中
                stringRedisTemplate.opsForList()
                        .rightPush(RedisConstants.SECKILL_ORDER_KEY+":queue",
                        JSONUtil.toJsonStr(voucherOrder));

//        5.返回订单id
        return Result.ok(orderId);
    }
}

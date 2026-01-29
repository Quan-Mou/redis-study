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
import com.hmdp.utils.UserHolder;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

//       悲观锁

        /**
         * intern()加入常量池
         * 锁对象是当前登录的用户id，只针对同一个用户并发问题，如果是不用的用户这里不会有锁竞争，也就是没有并发问题
         */
        Long orderId = null;
        synchronized(UserHolder.getUser().getId().toString().intern()) {

            QueryWrapper<VoucherOrder> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id",UserHolder.getUser().getId());
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
        }
//        5.返回订单id
        return Result.ok(orderId);
    }
}

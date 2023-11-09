package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. check voucher info
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. validate seckill activity starts or not
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("Second Kill has not yet begun!");
        }
        // 3. validate seckill alread end or not
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("Second kill is over");
        }

        // 4. validate stock is enough or not
        if (voucher.getStock() < 1) {
            return Result.fail("Not enough stock!");
        }

        // 5. create order without duplicated users
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            // get the proxy object (transactional)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        // 5. create order without duplicated users
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {            // 5.1 check did user create order before with voucher
            int count = query().eq("user_id", userId)
                    .eq("voucher_id", voucherId).count();
            // 5.2 validate
            if (count > 0) {
                return Result.fail("User " + userId + " was bought before");
            }

            // 6. deduct stock
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                return Result.fail("Not enough stock!");
            }

            // 7 create order
            // 7.1 order ID
            long orderId = redisIdWorker.nextId("order");
            VoucherOrder voucherOrder = VoucherOrder.builder()
                    .id(orderId)
                    .userId(userId)
                    .voucherId(voucherId)
                    .build();
            save(voucherOrder);

            // 8. return orderId
            return Result.ok(orderId);

        }
    }
}
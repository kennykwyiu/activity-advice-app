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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
*
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
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

        // 5. deduct stock
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            return Result.fail("Not enough stock!");
        }

        // 6. create order
        // 6.1 order ID
        long orderId = redisIdWorker.nextId("order");
        // 6.2 user ID
        Long userId = UserHolder.getUser().getId();
        // 6.3 voucher ID

        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId)
                .userId(userId)
                .voucherId(voucherId)
                .build();
        save(voucherOrder);

        // 7. return orderId
        return Result.ok(orderId);
    }
}

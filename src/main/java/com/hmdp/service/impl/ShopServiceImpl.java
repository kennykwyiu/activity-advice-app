package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * service implementation class
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryById(Long id) {
        // get the shop without CachePenetration problem
        // Shop shop = queryWithSolvingCachePenetration(id);

        // Mutex lock for solving cache breakdown
        Shop shop = queryWithMutexLock(id);
        if (shop == null) {
            return Result.fail("Shop is not exist");
        }
        // 7. return
        return Result.ok(shop);
    }

    public Shop queryWithMutexLock(Long id) {
        // 1. check shop info from Redis
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. validate is it exist
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. if exist, return the shop info
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // even it's not null, still need to validate the value is ""string or not
        if (Objects.equals(shopJson, "")) {
            // return fail msg
            return null;
        }
        // 4 implement re-build cache
        // 4.1 get mutex lock
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2 validate can it get
            if (!isLock) {
                // 4.3 if fail, go to sleep
                Thread.sleep(50);
                return queryWithMutexLock(id);
            }
            // 4.4 if succeed to get the lock, check from mySQL based on shopId
            shop = getById(id);
            // produce delay in re-build in cache
            Thread.sleep(200);
            // 5. if not exist in mySQL, return false
            if (shop == null) {
                // set null into Redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6. if exist in mySQL, save it in Redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. releases mutex lock
            unlock(lockKey);
        }
        // 8. return
        return shop;
    }

    public Shop queryWithSolvingCachePenetration(Long id) {
        // 1. check shop info from Redis
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. validate is it exist
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. if exist, return the shop info
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // even it's not null, still need to validate the value is ""string or not
        if (Objects.equals(shopJson, "")) {
            // return fail msg
            return null;
        }

        // 4. if not exist, check from mySQL based on shopId
        Shop shop = getById(id);
        // 5. if not exist in mySQL, return false
        if (shop == null) {
            // set null into Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. if exist in mySQL, save it in Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7. return
        return shop;
    }

    /**
     * The result of the setIfAbsent method is assigned to the flag variable, which is of type Boolean. The flag variable will be true if the lock is successfully acquired (key set in Redis) and false otherwise (key already exists).
     * The BooleanUtil.isTrue(flag) method is then used to convert the flag variable to a primitive boolean value. If flag is true, the method returns true; otherwise, it returns false.
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("Shop Id cannot be null");
        }
        // 1. update mySQL
        updateById(shop);

        // 2. delete Redis
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}

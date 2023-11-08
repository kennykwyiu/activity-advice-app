package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // set logical expire
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)))
                .build();
        // write into Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithSolvingCachePenetration(String keyPrefix,
                                                      ID id,
                                                      Class<R> type,
                                                      Function<ID, R> dbFallback,
                                                      Long time,
                                                      TimeUnit unit) {
        // 1. check shop info from Redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. validate is it exist
        if (StrUtil.isNotBlank(json)) {
            // 3. if exist, return the shop info
            return JSONUtil.toBean(json, type);
        }
        // even it's not null, still need to validate the value is ""string or not
        if (Objects.equals(json, "")) {
            // return fail msg
            return null;
        }

        // 4. if not exist, check from mySQL based on shopId
        R r = dbFallback.apply(id);
        // 5. if not exist in mySQL, return false
        if (r == null) {
            // set null into Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. if exist in mySQL, save it in Redis
        this.set(key, r, time, unit);
        // 7. return
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpired(String keyPrefix,
                                             ID id,
                                             Class<R> type,
                                             Function<ID, R> dbFallback,
                                             Long time,
                                             TimeUnit unit) {
        // 1. check shop info from Redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. validate is it exist
        if (StrUtil.isBlank(json)) {
            // 3. if not exist, return null
            return null;
        }
        // 4. if exist, need to deserialize json to an object
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. validate is it expired
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 if not expired, return shop info
            return r;
        }
        // 5.2 if expired, need to cache re-build
        // 6 cache re-build
        // 6.1 get the mutex lock
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 validate can it get the mutex lock
        if (isLock) {
            // 6.3 if get the lock, start new thread to implement cache re-build
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // re-build the cache
                    // check database
                    R r1 = dbFallback.apply(id);
                    // write into Redis
                    this.setWithLogicalExpire(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 6.4 if cannot get lock, return expired shop info
        // 7. return
        return r;
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


}

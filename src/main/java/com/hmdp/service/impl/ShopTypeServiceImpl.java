package com.hmdp.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeListFromRedis() {
        // Retrieve the list of shop type strings from Redis
        List<String> shopTypeStrings = stringRedisTemplate.opsForList()
                .range(CACHE_SHOP_KEY, 0, 9);

        // If the list is not null and not empty, deserialize it into a list of ShopType objects
        if (shopTypeStrings != null && !shopTypeStrings.isEmpty()) {
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeStrings.get(0), ShopType.class);
            return Result.ok(shopTypes);
        }

        // If the shop types are not available in Redis, query them from the database
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // If no shop types are found, return a failure result
        if (Objects.isNull(shopTypes)) {
            return Result.fail("Shop type data is empty");
        }

        // Serialize the shop types into a JSON string
        String jsonStr = JSONUtil.toJsonStr(shopTypes);

        // Store the shop types in Redis for future use
        stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_KEY, jsonStr);

        // Set an expiration time for the Redis key
        stringRedisTemplate.expire(CACHE_SHOP_KEY, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shopTypes);
    }
}

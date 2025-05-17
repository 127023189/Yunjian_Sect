package com.yunjian.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yunjian.dto.Result;
import com.yunjian.entity.Shop;
import com.yunjian.mapper.ShopMapper;
import com.yunjian.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yunjian.utils.CacheClient;
import com.yunjian.utils.RedisConstants;
import com.yunjian.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.yunjian.utils.RedisConstants.CACHE_SHOP_TTL;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
       Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,this::getById,
               CACHE_SHOP_TTL, TimeUnit.MINUTES);
       return Result.ok(shop);
    }

    private void saveShop2Redis(Long id, Long expireSeconds){
        Shop shop = getById(id);

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 写入Redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithLogicalExpire(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String json = stringRedisTemplate.opsForValue().get(key);

        // 不存在
        if(StrUtil.isBlank(json)){
            return null;
        }

        // 命中
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }

        // 过期，需要重建缓存
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return shop;
    }

    /**
     * 互斥锁
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 从redis里面查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //存在redis
        if(StrUtil.isNotBlank(shopJson)){
            Shop  shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // 空值的情况
        if(shopJson != null){
            return null;
        }

        // 开始实现缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;

        //1 获取互斥锁
        boolean isLock = tryLock(lockKey);
        try {
            if(!isLock){
                // 获取锁失败，休眠等待
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 成功查询数据库
            shop = getById(id);
            if(shop == null){
                // 缓存穿透,写入空值
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 缓存重建
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            unlock(lockKey);
        }
        return shop;
    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        // 1 更新数据库
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }

        updateById(shop);

        // 2 删除缓存
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();

        stringRedisTemplate.delete(key);

        return Result.ok();
    }
}

package com.yunjian.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yunjian.dto.Result;
import com.yunjian.entity.ShopType;
import com.yunjian.mapper.ShopTypeMapper;
import com.yunjian.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yunjian.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 查询类型列表
     */
    @Override
    public Result queryList() {
        String key = RedisConstants.CACHE_TYPE_LIST;

        // 先查redis
        String typeListJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(typeListJson)){
            List<ShopType> list = JSONUtil.toList(typeListJson, ShopType.class);
            return Result.ok(list);
        }

        // 为空先查数据库
        List<ShopType> list = query().orderByAsc("sort").list();

        if(list == null){
            return Result.fail("未查询到数据");
        }
        log.info("查询数据库");

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(list));
        return Result.ok(list);

    }
}

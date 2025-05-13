package com.yunjian.service;

import com.yunjian.dto.Result;
import com.yunjian.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 新增商铺
     *
     * @param shop
     * @return
     */
    Result update(Shop shop);
}

package com.yunjian.service;

import com.yunjian.dto.Result;
import com.yunjian.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询列表
     * @return
     */
    Result queryList();
}

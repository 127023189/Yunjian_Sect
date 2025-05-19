package com.yunjian.service;

import com.yunjian.dto.Result;
import com.yunjian.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注
     * @param id
     * @param isFollow
     * @return
     */
    Result follow(Long id, Boolean isFollow);

    /**
     * 取消关注
     * @param id
     * @return
     */
    Result isFollow(Long id);

    /**
     * 共同关注
     * @param id
     * @return
     */
    Result commonFollow(Long id);
}

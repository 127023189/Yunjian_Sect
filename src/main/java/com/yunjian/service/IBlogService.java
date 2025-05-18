package com.yunjian.service;

import com.yunjian.dto.Result;
import com.yunjian.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * * 保存探店博文
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * * 查询热搜博文
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * * 点赞博文
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * * 查询博文点赞排行
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);
}

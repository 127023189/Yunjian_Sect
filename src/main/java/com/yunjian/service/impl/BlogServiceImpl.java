package com.yunjian.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunjian.dto.Result;
import com.yunjian.dto.UserDTO;
import com.yunjian.entity.Blog;
import com.yunjian.entity.User;
import com.yunjian.mapper.BlogMapper;
import com.yunjian.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yunjian.service.IUserService;
import com.yunjian.utils.SystemConstants;
import com.yunjian.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yunjian.utils.RedisConstants.BLOG__LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * * 根据id查询博文
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog); // 查询用户

        // 查询是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if(user == null) {
            return;
        }
        // 获取当前登录用户
        Long userId = user.getId();

        String key = BLOG__LIKED_KEY + blog.getId();
        // 查询redis中是否有该用户的点赞记录
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        // 设置是否点赞
        blog.setIsLike(BooleanUtil.isTrue(score != null));
    }

    /**
     * * 封装blog对象
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            // 封装blog对象
            queryBlogUser(blog);
            // 查询是否被点赞
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * * * 点赞博文
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();

        // 到redis中查集合是否有，有则是点赞完成
        String key = BLOG__LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 未点赞

            // 这里先修改数据库，在redis中添加

            // 数据库点赞加+1
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();

            // 点赞成功
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }else {
                // 已经点赞，则取消
                boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();

                if (isSuccess) {
                    stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                }
            }
        return Result.ok();
    }

    /**
     * * 查询博文点赞排行
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG__LIKED_KEY + id;

        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()) {
            return Result.ok();
        }

        // 解析出id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据id查询用户
        String idsStr = StrUtil.join(",", ids);

        List<UserDTO> userList = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idsStr + ")")
                .list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 返回结果
        return Result.ok(userList);
    }
}

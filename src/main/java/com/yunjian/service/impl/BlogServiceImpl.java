package com.yunjian.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunjian.dto.Result;
import com.yunjian.dto.ScrollResult;
import com.yunjian.dto.UserDTO;
import com.yunjian.entity.Blog;
import com.yunjian.entity.Follow;
import com.yunjian.entity.User;
import com.yunjian.mapper.BlogMapper;
import com.yunjian.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yunjian.service.IFollowService;
import com.yunjian.service.IUserService;
import com.yunjian.utils.SystemConstants;
import com.yunjian.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yunjian.utils.RedisConstants.BLOG__LIKED_KEY;
import static com.yunjian.utils.RedisConstants.FEED_KEY;

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

    @Resource
    private IFollowService followService;

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

    /**
     * * * * 保存博文
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存博文
        boolean isSuccess = save(blog);
        if(!isSuccess) {
            return Result.fail("保存博文失败");
        }

        // 查询当前作者的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow:follows){
            // 获取粉丝id
            Long followerId = follow.getUserId();

            // 推送信息到粉丝的redis中
            String key = "feed:" + followerId;

            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    /**
     * * * 查询我关注的人的博文,实现滚动查询
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Long offset) {
        // 1 获取当前用户
        Long userId = UserHolder.getUser().getId();

        //2 查询收件箱
        String key = FEED_KEY + userId;
        // 参数 说明 count 2 查询两条
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        long os = 1; // 分数值等于最小元素的个数


        // 解析数据
        for(ZSetOperations.TypedTuple<String> tuple:typedTuples) {
            // 获取博文id
            String id = tuple.getValue();
            ids.add(Long.valueOf(id));
            // 获取博文时间,也就是分数
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os : os + offset; // 如果最大值等于最小值，则偏移量加1

        // 根据id查询blog
        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD(id," + idsStr + ")").list();

        for(Blog blog:blogs) {
            // 封装blog对象
            queryBlogUser(blog);
            // 查询是否被点赞
            isBlogLiked(blog);
        }

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset((int)os);

        return Result.ok(scrollResult);
    }
}

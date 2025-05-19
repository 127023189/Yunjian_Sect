package com.yunjian.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yunjian.dto.Result;
import com.yunjian.dto.UserDTO;
import com.yunjian.entity.Follow;
import com.yunjian.mapper.FollowMapper;
import com.yunjian.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yunjian.service.IUserService;
import com.yunjian.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 关注
     * @param id
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;


        // 判断是否关注
        if (isFollow) {

            // 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = this.save(follow);
            if(isSuccess){
                // 把个人关注的用户存入redis
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        }else{
            // 取消关注,删除
            boolean success = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if(success){
                // 删除redis
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询是否关注
     * @param id
     * @return
     */
    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();

        Integer count =query().eq("user_id", userId).eq("follow_user_id", id).count();

        return Result.ok(count == null ? false : count>0);
    }

    @Override
    public Result commonFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId; // 当前用户的关注列表
        String key1 = "follows:" + id; // 目标用户的关注列表
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key1);// 交集
        if(intersect == null || intersect.isEmpty()){
            return Result.ok();
        }

        // 有交集
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询用户
        List<UserDTO> users = userService.listByIds(ids).stream().map(user ->
           BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}

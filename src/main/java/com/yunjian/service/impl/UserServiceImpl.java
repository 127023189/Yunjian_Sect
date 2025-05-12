package com.yunjian.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yunjian.dto.LoginFormDTO;
import com.yunjian.dto.Result;
import com.yunjian.dto.UserDTO;
import com.yunjian.entity.User;
import com.yunjian.mapper.UserMapper;
import com.yunjian.service.IUserService;
import com.yunjian.utils.RegexUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.yunjian.utils.RedisConstants.*;
import static com.yunjian.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)){
            //手机号格式错误
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);

        // 保存到session
        //session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        System.out.println("code = " + code);

        return Result.ok();
    }

    /**
     * 登录功能
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 1. 校验手机号
            return Result.fail("手机号格式错误");
        }

        // 校验验证码
        String code = loginForm.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }

        // 一致先查询
        User user = this.query().eq("phone", phone).one();

        if(user == null){
            // 不存在则创建
            user = createUserWithPhone(phone);
        }

        // 保存用户信息到session
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 保存到redis中
        String token = UUID.randomUUID().toString();

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                    CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue) ->
                         String.valueOf(fieldValue)));

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    /**
     * 创建用户
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        this.save(user);
        return user;
    }
}

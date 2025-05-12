package com.yunjian.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yunjian.dto.Result;
import com.yunjian.entity.User;
import com.yunjian.mapper.UserMapper;
import com.yunjian.service.IUserService;
import com.yunjian.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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
        session.setAttribute("code", code);

        log.debug("发送短信验证码成功，验证码：" + code);

        return Result.ok();
    }
}

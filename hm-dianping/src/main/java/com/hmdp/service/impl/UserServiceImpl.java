package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private RedisTemplate redisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        /**
         * 存储在session中的做法
         */
////        1.校验手机号
//        if (RegexUtils.isPhoneInvalid(phone)) {
//            return Result.fail("请输入正确的手机号");
//        }
////        2.生成验证码
//        String code = RandomUtil.randomString(6);
//        log.info("code: {}", code);
////        3.保存验证码
//        session.setAttribute("code", code);


        /**
         * 存储在Redis中的做法
         */
        //        1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("请输入正确的手机号");
        }
//        2.生成验证码
        String code = RandomUtil.randomString(6);
        log.info("code: {}", code);
//        3.保存验证码
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }

    @Override
    public Result loginAndRegister(LoginFormDTO loginForm, HttpSession session) {

        /**
         * session保存在单机服务器中的做法
         */
////        1.校验手机号
//        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
//            return Result.fail("请输入正确的手机号");
//        }
////        2.比对验证码
////        3.如果验证码不匹配，返回验证码错误
//        String code = (String)session.getAttribute("code");
//        if (!code.equals(loginForm.getCode())) {
//            return Result.fail("请输入正确的验证码");
//        }
////        4.如果验证码匹配，调用数据查看该用户是否存在，如果不存在，则新增该用户信息
//        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
//        userQueryWrapper.eq("phone", loginForm.getPhone());
//        User user = this.getOne(userQueryWrapper);
//        if (user == null) {
//            user = BeanUtil.copyProperties(loginForm, User.class);
//            user.setNickName("user_" + RandomUtil.randomString(10));
//            this.save(user);
//        }
////        5.如果存在，则获取该用户信息
////        6.保存用户信息到session中
////        UserHolder.saveUser(BeanUtil.copyProperties(user, UserDTO.class));
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));


        /**
         * 使用Redis中的做法
         */

        //        1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("请输入正确的手机号");
        }
//        2.比对验证码
//        3.如果验证码不匹配，返回验证码错误
        Object code = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        assert code != null;
        if (!code.toString().equals(loginForm.getCode())) {
            return Result.fail("请输入正确的验证码");
        }
//        4.如果验证码匹配，调用数据查看该用户是否存在，如果不存在，则新增该用户信息
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("phone", loginForm.getPhone());
        User user = this.getOne(userQueryWrapper);
        if (user == null) {
            user = BeanUtil.copyProperties(loginForm, User.class);
            user.setNickName("user_" + RandomUtil.randomString(10));
            this.save(user);
        }
//        5.如果存在，则获取该用户信息
//        6.保存用户信息到redis中，生一个一个唯一随机的数加入到key中，并且返回，后续请求都需要再请求头中携带这个key，名为token
//        UserHolder.saveUser(BeanUtil.copyProperties(user, UserDTO.class));
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        String key = RedisConstants.LOGIN_USER_KEY  +  RandomUtil.randomString(10);

        redisTemplate.opsForValue().set(key ,BeanUtil.copyProperties(user, UserDTO.class));


//       把这个key返回，后续请求都携带返回的key进行校验
        return Result.ok(key);
    }

    @Override
    public Result getUserById(Long id) {
        User user = getById(id);
        if (user == null) {
            return Result.fail("用户不存在！");

        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }
}

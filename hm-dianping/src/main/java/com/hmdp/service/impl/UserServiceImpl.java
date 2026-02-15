package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Sign;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.ISignService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISignService signService;

    @Value("${JWTSecret}")
    private String jwtSecret;


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
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok(code);
    }

    @Override
    public Result loginAndRegister(LoginFormDTO loginForm, HttpSession session) {

        /**
         * session保存在单机服务器中的做法
         */
//        1.校验手机号
//        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
//            return Result.fail("请输入正确的手机号");
//        }
////        2.比对验证码
////        3.如果验证码不匹配，返回验证码错误
////        String code = (String)session.getAttribute("code");
//        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
//        if(code == null) {
//            return Result.fail("请先获取验证码");
//        }
//
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
//        session.setMaxInactiveInterval(60*60*24*7); // 设置Session过期时间为一个星期
////        5.如果存在，则获取该用户信息
////        6.保存用户信息到session中
////        UserHolder.saveUser(BeanUtil.copyProperties(user, UserDTO.class));
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//        UserHolder.saveUser(user);
//        return Result.ok();

        /**
         * 使用Redis中的做法
         */

        //        1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("请输入正确的手机号");
        }
//        2.比对验证码
//        3.如果验证码不匹配，返回验证码错误
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        assert code != null;
        if (!code.equals(loginForm.getCode())) {
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
//      如果使用JWT生成token，主要是防伪（防止修改），那就可以不使用redis来存储这个信息，弊端是无法主动过期，这里为了应用redis，还是把这个token加入到redis中了，优点是登录可控
        HashMap<String, Long> map = new HashMap<>();
        map.put("userId", user.getId());
        String token = JWT.create()
                .addPayloads(map)
                .setExpiresAt(DateUtil.offsetDay(new Date(), 7))
                .setSigner(JWTSignerUtil.hs256(jwtSecret.getBytes()))
                .sign();

        String key = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForValue().set(key , JSONUtil.toJsonStr(user), RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        UserHolder.saveUser(BeanUtil.copyProperties(user,UserDTO.class));
//       把这个key返回，后续请求都携带返回的key进行校验
        return Result.ok(token);

    }

    @Override
    public Result getUserById(Long id) {
        User user = getById(id);
        if (user == null) {
            return Result.fail("用户不存在！");

        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    @Override
    public Result sign() {
//        1.判断当天是否签到
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonth().getValue();
        int day = now.getDayOfMonth();
        Long userId = UserHolder.getUser().getId();
        if(userId == null){
            return Result.fail("请先登录");
        }
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + year + "_" + month;
        if (stringRedisTemplate.hasKey(key)) {
//          当前月有过签到记录,判断是否签到过，没签则签到，签了直接返回
            if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, day - 1))) {
                return Result.ok("已经签到过了");
            }
            stringRedisTemplate.opsForValue().setBit(key,day-1,true);
            Sign sign = new Sign();
            sign.setUserId(userId);
            sign.setYear(year);
            sign.setMonth((byte) month);
            sign.setDate(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()));
            signService.save(sign);
            return Result.ok(true);
        }
//        如果没签到，则去签到，签到信息每个用户的一个月为一个key：sign:userId:年份_月份 = 31的bit
//        先判断当月有没有签到过，如果当前月没有签到过，创建key，并且计算当前天是该月的第几天，offset添加bit为1，当前天之前的天数都设置为0(默认为0)
        stringRedisTemplate.opsForValue().setBit(key,day-1,true);
        Sign sign = new Sign();
        sign.setUserId(userId);
        sign.setYear(year);
        sign.setMonth((byte) month);
        sign.setDate(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()));
        signService.save(sign);
        return Result.ok(true);
    }

    @Override
    public Result signCount() {
        return Result.ok(statisticContinuousSign());
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        if(token == null){
            return Result.fail("请先登录");
        }
//        删除redis中的token即可
        Long userId = UserHolder.getUser().getId();
        if(userId == null){
            return Result.fail("先登录");
        }
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        return Result.ok();
    }

    /**
     * 统计月份签到次数
     * @param now
     * @return
     */
    public long statisticSign(LocalDateTime now) {
        Long userId = UserHolder.getUser().getId();
        String key =  RedisConstants.USER_SIGN_KEY + userId + ":" + now.getYear() + "_" + now.getMonth().getValue();
        return stringRedisTemplate.execute(
                (RedisCallback<Long>) connection ->
                        connection.bitCount(key.getBytes(StandardCharsets.UTF_8))
        );
    }

    /**
     * 统计当前用户的连续签到天数
     */
    public long statisticContinuousSign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key =  RedisConstants.USER_SIGN_KEY + userId + ":" + now.getYear() + "_" + now.getMonth().getValue();
        int day = LocalDateTime.now().getDayOfMonth();
        List<Long> result = stringRedisTemplate.execute(
                (RedisCallback<List<Long>>) connection -> {
                    return connection.bitField(
                            key.getBytes(StandardCharsets.UTF_8),
                            BitFieldSubCommands.create()
                                    .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
                    );
                }
        );
        if(result == null || result.isEmpty()){
            return 0L;
        }
        Long byteSign = result.get(0);
        if(byteSign == null){
            return 0L;
        }
        String byteString = Long.toBinaryString(byteSign);
        int length = byteString.length();
        int count  = 0;
        for (int i = length -1; i>=0 ; i--) {
            char byteV = byteString.charAt(i);
            if(byteV == '1') {
                count++;
            }
            if(byteV == '0') {
                break;
            }
        }
        return count;
    }

}
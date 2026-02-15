package com.hmdp.handler;

import cn.hutool.crypto.KeyUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class InterceptHandler implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${JWTSecret}")
    private String jwtSecret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String token = request.getHeader("authorization");
        if(token == null){
            response.setStatus(401);
            return false;
        }
//      解析JWT
        JWT jwt = JWTUtil.parseToken(token);
//        无效的token
        if (!jwt.verify(JWTSignerUtil.hs256(jwtSecret.getBytes()))) {
            response.setStatus(401);
            return false;
        }
        String user = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_USER_KEY + token);
        if(user == null){
            response.setStatus(401);
            return false;
        }

        System.out.println(UserHolder.getUser());
        if(UserHolder.getUser() == null) {
            UserHolder.saveUser(JSONUtil.toBean(user,UserDTO.class));
        }
        return true;

//        Session的方式
//        UserDTO user = (UserDTO)request.getSession().getAttribute("user");
//        if(user == null){
//            response.setStatus(401);
//            return false;
//        }
//        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}

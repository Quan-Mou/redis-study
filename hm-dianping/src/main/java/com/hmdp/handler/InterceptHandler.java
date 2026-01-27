package com.hmdp.handler;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class InterceptHandler implements HandlerInterceptor {


    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        UserDTO user = (UserDTO)request.getSession().getAttribute("user");
        String token = request.getHeader("authorization");

        Object user = redisTemplate.opsForValue().get(token);
        System.out.println("Redis receive User" + user);
        if(user == null){
            response.setStatus(401);
            return false;
        }
//      说明已经登录过，把user存入Threadlocal中
        UserHolder.saveUser((UserDTO) user);
        return true;


//        UserDTO user = (UserDTO)request.getSession().getAttribute("user");
//        if(user == null){
//            response.setStatus(401);
//            return false;
//        }
////      说明已经登录过，把user存入Threadlocal中
//        UserHolder.saveUser(user);
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

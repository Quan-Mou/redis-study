package com.hmdp.config;

import com.hmdp.handler.InterceptHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private InterceptHandler interceptHandlerConfig;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptHandlerConfig)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/shop-type/**",
                        "/shop/**",
                        "/upload/**",
                        "/blog/hot");
    }
}

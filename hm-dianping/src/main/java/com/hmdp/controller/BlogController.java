package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.BlogPageResponse;
import com.hmdp.dto.FollowBlogVO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.websocket.server.PathParam;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
@Slf4j
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 返回用户发布的博客列表
     * @param id 用户id
     * @param current
     * @return
     */
    @GetMapping("/of/user")
    public Result userBlog(@PathParam("id") Long id,@PathParam("current") Long current) {
        return blogService.getUserBlog(id,current);
    }

    /**
     * 推送的关注的人的博客-推模式
     * @return
     */
    @GetMapping("/of/follow")
    public Result follow(@RequestParam("lastId") Long lastId,@RequestParam(value = "offset",required = false,defaultValue = "0") Integer offset) {
        return blogService.getFollowBlog(lastId,offset);
    }


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 返回点赞博客的用户信息
     * @param id 博客id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result like(@PathVariable("id") Long id) {
        return blogService.like(id);
    }

    @GetMapping("/{id}")
    public Result getBlogById(@PathVariable("id") Long id) {
       return blogService.queryBlogByID(id);
    }

    /**
     * 博客点赞
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.goLike(id);
    }


    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.ofMe(current);
    }


    /**
     * 根据点赞量查询博文
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }
}

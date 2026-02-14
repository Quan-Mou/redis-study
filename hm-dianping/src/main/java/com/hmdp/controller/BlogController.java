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

    @Resource
    private IFollowService  followService;

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

//        发布者：
//        1.把内容保存到mysql
//        2.主动把当前发布的blogId添加到粉丝的收件箱（zset），如果粉丝收件箱不存在，则帮粉丝创建这个收件箱
//        3.返回ok

//        接收者：
        Long userId = UserHolder.getUser().getId();

//        2.获取信箱我的（如果不存在说明是第一次，创建这个信箱）的所有id，根据score排名，降序
//        根据信箱内的id，批量查询对应的blog
        Long size = stringRedisTemplate.opsForZSet().size(RedisConstants.USER_FOLLOW_KEY + userId);
        if(size == null || size == 0) {
//           没有关注的人或者关注的人没有发过博文
            return Result.ok(Collections.emptyList());
        }
//        zrevrangebyscore key max min withscores limit offset count
        List<Long> blogIds = new ArrayList<>();
        AtomicLong minTime = new AtomicLong(System.currentTimeMillis());
        stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(
                        RedisConstants.USER_LETTERBOX_KEY + userId,
                        0,
                        lastId, offset, 3
                ).stream().forEach(item -> {
                    blogIds.add(Long.parseLong(item.getValue()));
                    if(item.getScore() < minTime.get()) {
                        minTime.set(item.getScore().longValue());
                    }
                });
        if(blogIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        log.info("minTime：{}",minTime.get());
//        typedTuples.
//        4.返回所有的blog(含用户信息和对应的blog，按照时间降序)
        List<Blog> blogs = blogService.query().in("id", blogIds).orderByDesc("update_time").list();
        if(blogs.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        Long followIdSize = stringRedisTemplate.opsForZSet().size(RedisConstants.USER_FOLLOW_KEY + userId);
        if(followIdSize == null) {
            return Result.ok(Collections.emptyList());
        }
        Set<String>  followIds = stringRedisTemplate.opsForZSet().range(RedisConstants.USER_FOLLOW_KEY + userId, 0, followIdSize - 1);
        List<FollowBlogVO> result = new ArrayList<>();
//      过滤已经取关的blogId
        List<Long> userIds = blogs.stream().filter(item -> {
            return followIds.contains(item.getUserId().toString());
        }).map(Blog::getUserId).collect(Collectors.toList());
        if(userIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        String idsStr = userIds.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));

        List<User> users = userService.query().in("id ", userIds).last("order by field(id," + idsStr + ")").list();
//        TODO：先这样写,另外hasMore暂时没写
            for(Blog blog : blogs) {
                for(User user : users) {
                    if(blog.getUserId().equals(user.getId())) {
                        FollowBlogVO item = new FollowBlogVO();
                        item.setId(blog.getId());
                        item.setImages(blog.getImages());
                        item.setTitle(blog.getTitle());
                        item.setLiked(blog.getLiked());
                        item.setIcon(user.getIcon());
                        item.setName(user.getNickName());
                        item.setCreateTime(blog.getCreateTime().toEpochSecond(ZoneOffset.UTC));
                        result.add(item);
                    }
                }
            }
        return Result.ok(new BlogPageResponse(result,minTime.get(),offset,false));
    }


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        Long fanSize = stringRedisTemplate.opsForZSet().size(RedisConstants.USER_FANS_KEY + user.getId());
        if(fanSize != null) {
            Set<String> fanIds = stringRedisTemplate.opsForZSet().range(RedisConstants.USER_FANS_KEY + user.getId(), 0, fanSize);
            if(fanIds != null && !fanIds.isEmpty()) {
                fanIds.forEach(fanId -> {
//                   粉丝的收信箱是懒创建的
                    // 获取粉丝的收信箱，把博文id存进去
                    Boolean isSuccess = stringRedisTemplate.opsForZSet().add(RedisConstants.USER_LETTERBOX_KEY + fanId, blog.getId().toString(), blog.getUpdateTime().toEpochSecond(ZoneOffset.UTC));
                });
            }
        }
        // 返回id
        return Result.ok(blog.getId());
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
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), user.getId().toString());
            blog.setIsLike(!(score == null));
        });

        return Result.ok(records);
    }
}

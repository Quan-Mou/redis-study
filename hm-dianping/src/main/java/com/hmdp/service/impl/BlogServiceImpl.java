package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private UserServiceImpl userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogByID(Long id) {

        Blog blog = getById(id);
        if(blog == null) {
            return Result.fail("Blog不存在");
        }
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        blog.setIsLike(isLiketd(id));
        return Result.ok(blog);
    }


    @Override
    public Result like(Long id) {
        /**
         * blog表中只记录了点赞数量
         * 如何实现用户是否点赞，一人一赞，并且可以排序，zset
         * zset设计：key：blog:like:7，value为用户id
         * 思考：点赞信息存储在redis不在mysql中做持久化安全吗？
         */
        Set<String> likeUserId = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4); // 升序
        if(likeUserId != null && likeUserId.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIds = likeUserId.stream().map(Long::parseLong).collect(Collectors.toList());
        /**
         * SELECT id,phone,password,nick_name,icon,create_time,update_time FROM tb_user WHERE id IN ( ? , ? )
         * 其返回结果的顺序是不确定的，并不保证按照 IN 子句中参数的顺序返回。
         * 使用field(vlaue,v1,v2,v3)：返回vlaue在后面参数中第一次出现的索引位置，索引从1开始
         *
         */
//        likeUserId.stream().map(item -> String.join(","item))
        String idsStr = likeUserId.stream().collect(Collectors.joining(","));
        likeUserId.stream().collect(Collectors.joining(","));
//        SELECT id,phone,password,nick_name,icon,create_time,update_time FROM tb_user WHERE (id IN (?,?)) order by field(id,1012,1009)
        List<User> users = userService.query().in("id", userIds).last("order by field(id," + idsStr + ")").list();
        List<UserDTO> userDto = new ArrayList<>();
        users.forEach(user -> userDto.add(BeanUtil.copyProperties(user, UserDTO.class)));
        return Result.ok(userDto);
    }

    @Override
    public Result goLike(Long id) {
        Long userId = UserHolder.getUser().getId();
        long stamp = System.currentTimeMillis();
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        if(score == null) {
            //        2.不存在就把该用户添加到zset中
            stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY  + id,userId.toString(),stamp);
            update().setSql("liked = liked + 1").eq("id", id).update();

        } else {
            //        1.1 存在就删除
            stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id,userId.toString());
            update().setSql("liked = liked - 1").eq("id", id).update();
        }
        return Result.ok();
    }

    @Override
    public Result getUserBlog(Long id,Long current) {
        return Result.ok(query().eq("user_id", id).list());
    }

    public boolean isLiketd(Long id) {
        Long userId = UserHolder.getUser().getId();
        if(userId == null) {
            throw new RuntimeException("请先登录在点赞！");
        }
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        return !(score == null);
    }
}

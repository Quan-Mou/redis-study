package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.BlogPageResponse;
import com.hmdp.dto.FollowBlogVO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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

    @Override
    public Result getFollowBlog(Long lastId, Integer offset) {

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
        List<Blog> blogs = query().in("id", blogIds).orderByDesc("update_time").list();
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

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
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

    @Override
    public Result ofMe(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
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

    public boolean isLiketd(Long id) {
        Long userId = UserHolder.getUser().getId();
        if(userId == null) {
            throw new RuntimeException("请先登录在点赞！");
        }
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        return !(score == null);
    }
}

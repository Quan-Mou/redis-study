package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService  userService;

    @Override
    public Result follow(Long id) {
        User user = userService.getById(id);
        if(user == null) {
            return Result.fail("不存在的用户");
        }
//        1.数据库添加
        Long currentUserId = UserHolder.getUser().getId();
        QueryWrapper<Follow> query = new QueryWrapper<Follow>().eq("user_id", currentUserId).eq("follow_user_id", id);
        Follow isExist = getOne(query);
        if(isExist != null) {
            return Result.fail("不可重复关注");
        }
        Follow follow = new Follow();
        follow.setFollowUserId(id);
        follow.setUserId(currentUserId);
        save(follow);
//        2.缓存数据库添加
        long score = System.currentTimeMillis();
        stringRedisTemplate.opsForZSet().add(RedisConstants.USER_FOLLOW_KEY + currentUserId,id.toString(),score);
        return Result.ok();
    }

    @Override
    public Result unFollow(Long id) {
        User user = userService.getById(id);
        if(user == null) {
            return Result.fail("不存在的用户");
        }
        // 1.数据库删除
        Long currentUserId = UserHolder.getUser().getId();
        QueryWrapper<Follow> query = new QueryWrapper<Follow>().eq("user_id", currentUserId).eq("follow_user_id", id);
        boolean isRemove = this.remove(query);
        if(!isRemove) {
            return Result.fail("取关失败，稍后再试！");
        }
//        2.缓存数据库删除
        stringRedisTemplate.opsForZSet().remove(RedisConstants.USER_FOLLOW_KEY + currentUserId,id.toString());
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
//      查看缓存数据库中是否存在该用户
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.USER_FOLLOW_KEY + UserHolder.getUser().getId(), id.toString());
        return Result.ok(score != null);
    }

    @Override
    public Result commonFollow(Long id) {
        /**
         * 将参数1和参数2的交集保存到参数3这个zset中，也就是说参数3存的是参数1和参数2的共同好友
         */
//        stringRedisTemplate.opsForZSet().intersectAndStore("user:follow:1009", "user:follow:1012", "common:likers:1009_1012");
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.opsForZSet()
                .intersectAndStore(RedisConstants.USER_FOLLOW_KEY + userId,
                        RedisConstants.USER_FOLLOW_KEY + id,
                        RedisConstants.COMMON_FOLLOW_KEY + userId + "_" + id);
        if(result == null || result == 0) { // 添加失败
            return Result.ok(Collections.emptyList());
        }
        Long size = stringRedisTemplate.opsForZSet().size(RedisConstants.COMMON_FOLLOW_KEY + userId + "_" + id);
        List<Long> commonIds = stringRedisTemplate
                .opsForZSet()
                .reverseRange(RedisConstants.COMMON_FOLLOW_KEY + userId + "_" + id, 0, size)
                .stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = commonIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        List<UserDTO> users = userService
                .query()
                .in("id", commonIds)
                .last("order by field(id," + idStr + ")")
                .list().stream().map(item -> BeanUtil.copyProperties(item, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}

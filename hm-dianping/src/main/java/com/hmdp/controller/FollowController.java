package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
@Slf4j
public class FollowController {


    @Resource
    private IFollowService followService;

    /**
     * 查看共同关注的好友
     * @param id 用户id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id) {
        return  followService.commonFollow(id);
    }


    /**
     * 是否关注该blog的用户
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
       return  followService.isFollow(id);
    }

    /**
     * 关注用户
     * @param id
     * @return
     */
    @PutMapping("/{id}/true")
    public Result follow(@PathVariable("id") Long id) {
        return followService.follow(id);
    }

    /**
     * 取消关注用户
     * @param id
     * @return
     */
    @PutMapping("/{id}/false")
    public Result unFollow(@PathVariable("id") Long id) {
        return followService.unFollow(id);
    }

}

package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.websocket.server.PathParam;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogByID(Long id);

    Result like(Long id);

    Result goLike(Long id);

    Result getUserBlog(Long id,Long current);

    Result getFollowBlog(Long lastId, Integer offset);

    Result saveBlog(Blog blog);

    Result ofMe(Integer current);

    Result queryHotBlog(Integer current);
}

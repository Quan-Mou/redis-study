package com.hmdp.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 关注用户的博客信息（用于返回给前端）
 */
@Data
public class FollowBlogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 博客 ID
     */
    private Long id;

    /**
     * 博客标题
     */
    private String title;

    /**
     * 图片列表，多个 URL 用英文逗号分隔（如："url1,url2,url3"）
     */
    private String images;

    /**
     * 发布者头像（用户 icon）
     */
    private String icon;

    /**
     * 发布者昵称
     */
    private String name;

    /**
     * 点赞数量
     */
    private Integer liked;

    /**
     * 当前用户是否已点赞（true: 已点赞，false: 未点赞）
     */
    private Boolean isLike;

    /**
     * 创建时间（毫秒时间戳）
     */
    private Long createTime;
}
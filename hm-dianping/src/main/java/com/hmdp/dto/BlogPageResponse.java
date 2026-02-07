package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BlogPageResponse {

    private List<FollowBlogVO> list;
    private Long minTime;       // 下一次请求的游标（最后一条的时间戳）
    private Integer offset;     // 偏移量
    private Boolean hasMore;    // 是否还有更多数据
}
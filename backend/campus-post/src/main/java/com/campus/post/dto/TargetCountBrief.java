package com.campus.post.dto;

import lombok.Data;

/**
 * 按目标 id 聚合的计数（评论点赞数），t_like 批量统计用。
 */
@Data
public class TargetCountBrief {

    private Long targetId;

    private Integer cnt;
}

package com.campus.post.dto;

import lombok.Data;

/**
 * 按用户 id 聚合的计数（发帖数 / 评论数），用于算用户等级 LV。
 */
@Data
public class UserCountBrief {

    private Long userId;

    private Integer cnt;
}

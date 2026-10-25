package com.campus.admin.dto;

import lombok.Data;

/**
 * 用户内容量统计（详情页展示，只读聚合，不落库）。
 */
@Data
public class UserStats {

    private long posts;

    private long goods;

    private long wall;

    private long orders;
}

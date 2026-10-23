package com.campus.user.dto;

import lombok.Data;

/**
 * 关注 / 取消关注统一返回。
 * following：操作后的关注状态；fansCount：被关注者的最新粉丝数。
 */
@Data
public class FollowResp {

    private Boolean following;

    private Integer fansCount;
}

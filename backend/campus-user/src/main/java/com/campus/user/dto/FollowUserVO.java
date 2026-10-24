package com.campus.user.dto;

import lombok.Data;

/**
 * 关注 / 粉丝列表中的用户卡片。
 * 在 UserBrief 基础上增加关注数与 isFollowing，方便前端一次渲染。
 */
@Data
public class FollowUserVO {

    private Long id;

    private String nickname;

    private String avatar;

    private Long schoolId;

    private Integer verifyStatus;

    /** 该用户的粉丝数 */
    private Integer fansCount;

    /** 该用户的关注数 */
    private Integer followCount;

    /** 当前登录用户是否已关注对方（列表场景） */
    private Boolean isFollowing;
}

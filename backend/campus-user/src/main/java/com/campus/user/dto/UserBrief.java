package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserBrief {

    private Long id;

    private String nickname;

    private String avatar;

    private Long schoolId;

    private Integer verifyStatus;

    /**
     * 注销申请时间，null = 正常账号。
     *
     * <p>登录接口会原样带回：前端拿到非空值就先问用户「是否恢复账号」，
     * 用户拒绝则直接清掉本地登录态，不再进入主流程。
     */
    private LocalDateTime cancelledAt;
}

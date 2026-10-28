package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录 / 内部查询返回的用户摘要。
 *
 * <p><b>不要在纯展示场景直接用这个类</b>：它带着 {@code status} / {@code tokenVersion}
 * 两个安全字段，是给认证服务与网关做准入判断的。
 */
@Data
public class UserBrief {

    private Long id;

    private String nickname;

    private String avatar;

    private Long schoolId;

    private Integer verifyStatus;

    /**
     * 1 正常 / 0 封禁。
     *
     * <p>必须下发：以前这里没有它，认证服务拿到 UserBrief 也无从判断，
     * 「被封禁的人重开小程序 → 换一张新令牌」这条路一直是通的。
     */
    private Integer status;

    /**
     * 令牌版本。签发令牌时写进 claim {@code tv}，网关据此判定旧令牌是否已失效。
     * 封禁 / 解封 / 注销 / 恢复都会 +1。
     */
    private Integer tokenVersion;

    /**
     * 注销申请时间，null = 正常账号。
     *
     * <p>登录接口会原样带回：前端拿到非空值就先问用户「是否恢复账号」，
     * 用户拒绝则直接清掉本地登录态，不再进入主流程。
     */
    private LocalDateTime cancelledAt;
}

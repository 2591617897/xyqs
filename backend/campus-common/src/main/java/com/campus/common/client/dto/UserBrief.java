package com.campus.common.client.dto;

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
     * 1 正常 / 0 封禁。
     *
     * <p>这个字段决定了「封禁是否真的生效」：认证服务拿不到它就无从拒绝登录，
     * 被封禁的人只要重开小程序就能换到一张全新令牌（有效期 72 小时）。
     */
    private Integer status;

    /**
     * 令牌版本。签发时写进 JWT claim {@code tv}，网关比对库里的值判断旧令牌是否失效。
     * 缺失按 0 处理，与 {@code t_user.token_version DEFAULT 0} 对齐。
     */
    private Integer tokenVersion;

    /**
     * 注销申请时间，null = 正常账号。
     *
     * <p>登录响应必须带上它：小程序拿到非空值要先问「是否恢复账号」，
     * 用户拒绝就清掉本地登录态。少这个字段，前端只能看到「已注销用户」这个昵称，
     * 无法判断是注销还是用户自己改的名字。
     */
    private LocalDateTime cancelledAt;
}

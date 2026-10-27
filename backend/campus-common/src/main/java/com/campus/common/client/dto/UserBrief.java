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
     * 注销申请时间，null = 正常账号。
     *
     * <p>登录响应必须带上它：小程序拿到非空值要先问「是否恢复账号」，
     * 用户拒绝就清掉本地登录态。少这个字段，前端只能看到「已注销用户」这个昵称，
     * 无法判断是注销还是用户自己改的名字。
     */
    private LocalDateTime cancelledAt;
}

package com.campus.common.client.dto;

import lombok.Data;

@Data
public class LoginOrCreateReq {

    /** 未显式指定平台时的兜底值（微信小程序） */
    public static final String DEFAULT_PLATFORM = "wx";

    /** 宿主平台 wx / qq，缺省按 wx 处理 */
    private String platform;

    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;
}

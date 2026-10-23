package com.campus.user.dto;

import lombok.Data;

@Data
public class LoginOrCreateReq {

    /** 宿主平台 wx / qq；campus-auth 已做归一化，这里再兜一层防止历史调用方漏传 */
    private String platform;

    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;
}

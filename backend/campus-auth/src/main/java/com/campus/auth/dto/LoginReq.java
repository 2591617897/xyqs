package com.campus.auth.dto;

import lombok.Data;

@Data
public class LoginReq {

    /** qq.login() 获取的 code */
    private String code;

    private String nickname;

    private String avatar;

    /** 本地 mock 模式下的固定 openid，正式环境忽略 */
    private String devOpenid;
}

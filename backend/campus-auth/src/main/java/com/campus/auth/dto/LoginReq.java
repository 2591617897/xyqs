package com.campus.auth.dto;

import lombok.Data;

@Data
public class LoginReq {

    /** wx.login() / qq.login() 获取的 code */
    private String code;

    /** 宿主平台 wx / qq，缺省按 wx 处理 */
    private String platform;

    private String nickname;

    private String avatar;

    /** 本地 mock 模式下的固定 openid，正式环境忽略 */
    private String devOpenid;
}

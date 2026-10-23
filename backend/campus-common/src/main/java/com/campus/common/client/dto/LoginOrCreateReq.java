package com.campus.common.client.dto;

import lombok.Data;

@Data
public class LoginOrCreateReq {

    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;
}

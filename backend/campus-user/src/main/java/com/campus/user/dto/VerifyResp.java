package com.campus.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyResp {

    private Integer verifyStatus;

    /** 认证状态变化后重新签发的 token（内含最新 verifyStatus） */
    private String token;

    private String message;
}

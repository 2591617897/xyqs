package com.campus.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录结果。token 由前端放进 Authorization: Bearer 头。
 */
@Data
@AllArgsConstructor
public class LoginResp {

    private String token;

    /** 令牌有效期（小时），前端可据此提示重新登录 */
    private long expireHours;

    /** true 表示仍在用初始密码，前端应当强弹改密对话框 */
    private boolean mustChangePwd;
}

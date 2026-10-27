package com.campus.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发码结果。
 *
 * <p>只回脱敏邮箱（e***@example.com），完整地址不进前端 —— 页面截图、浏览器历史、
 * 前端日志都算是泄漏面，管理员邮箱没必要暴露得那么完整。
 */
@Data
@AllArgsConstructor
public class SendResetCodeResp {

    private String maskedEmail;

    /** 验证码有效期（分钟） */
    private int expireMinutes;

    /** 多少秒后才能再次发送 */
    private int resendSeconds;
}

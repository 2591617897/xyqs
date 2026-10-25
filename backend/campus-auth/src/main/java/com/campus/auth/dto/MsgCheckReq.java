package com.campus.auth.dto;

/**
 * 内部内容安全校验入参（仅供 campus-auth 的 /internal/security 使用）。
 *
 * @param userId  提交者 id，用于反查 openid
 * @param content 待校验文本
 * @param scene   微信场景值：1 资料 / 2 评论 / 3 论坛 / 4 社交日志
 */
public record MsgCheckReq(Long userId, String content, Integer scene) {
}

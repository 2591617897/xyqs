package com.campus.common.client.dto;

/**
 * 内容安全校验入参。
 *
 * @param userId  提交者 id
 * @param content 待校验文本
 * @param scene   微信场景值：1 资料 / 2 评论 / 3 论坛 / 4 社交日志
 */
public record MsgCheckReq(Long userId, String content, Integer scene) {
}

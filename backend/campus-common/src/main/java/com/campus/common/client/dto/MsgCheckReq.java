package com.campus.common.client.dto;

/**
 * 内容安全校验入参。
 *
 * @param userId  提交者 id
 * @param content 待校验文本
 * @param scene   微信场景值：1 资料 / 2 评论 / 3 论坛 / 4 社交日志
 * @param bizType 业务类型（POST/WALL/GOODS/P_COMMENT/W_COMMENT/G_COMMENT/MESSAGE/NICKNAME），
 *                仅用于违规事件落 t_content_audit 时标注来源，可为 null
 */
public record MsgCheckReq(Long userId, String content, Integer scene, String bizType) {

    /**
     * 兼容旧调用点：只关心文本与场景时不必传 bizType。
     * 保留这个重载是为了让 {@code SecurityGuard.checkText(userId, text, scene)} 的
     * 十几处既有调用不用改。
     */
    public MsgCheckReq(Long userId, String content, Integer scene) {
        this(userId, content, scene, null);
    }
}

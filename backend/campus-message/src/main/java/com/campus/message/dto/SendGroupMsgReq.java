package com.campus.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 发群消息请求。WebSocket 上行与 HTTP 兜底共用同一个结构，
 * 保证「两条通道发出来的消息格式完全一致」。
 */
@Data
public class SendGroupMsgReq {

    /** text / image（system 只允许服务端生成） */
    private String type;

    private String content;

    /**
     * 客户端幂等 id。前端每次发送生成一个 uuid；
     * 断线重发 / 用户连点都会撞 {@code uk_client}，服务端返回已存在的那条而不是插两条。
     */
    private String clientMsgId;

    /** @指定人，最多 10 个；元素是字符串 id（雪花超出 JS 安全整数） */
    private List<String> atUserIds;

    /** @所有人，仅群管理员可用，每人每天最多 3 次 */
    private Boolean atAll;

    /**
     * 被回复的消息 id（微信式「回复」）。为空表示普通消息。
     *
     * <p>服务端只接受本群内的消息：跨群引用会让用户点开引用块看到别人的私密内容。
     * 昵称与摘要由服务端取快照，<b>不信任前端传的内容</b>——否则可以伪造成任何人说的话。
     */
    private String replyToId;
}

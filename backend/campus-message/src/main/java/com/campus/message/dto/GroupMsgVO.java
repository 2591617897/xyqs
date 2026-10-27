package com.campus.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 群消息（对外 VO）。时间已格式化为字符串，id 全部是字符串
 * （雪花 19 位超出 JS 安全整数，前端 {@code Number(id)} 会把 …9601 变成 …9600）。
 */
@Data
public class GroupMsgVO {

    private String id;

    private String groupId;

    private String senderId;

    private String senderName;

    private String senderAvatar;

    /** text / image / system */
    private String type;

    private String content;

    /** 0 无 1 @指定人 2 @所有人 */
    private Integer atType;

    /** atType=1 时被 @ 的成员 id */
    private List<String> atUserIds;

    /** 是否 @所有人（前端据此显示「@全体」标签） */
    private Boolean atAll;

    /** 被回复的消息 id（前端点击引用块跳转到原消息） */
    private String replyToId;

    /** 被回复者昵称（快照，不随对方改昵称变化） */
    private String replyToName;

    /** 被回复内容摘要（快照） */
    private String replyToText;

    /**
     * 被回复消息的类型（text / image），由服务端按 replyToId 回查补全。
     *
     * <p>快照里只存了「[图片]」这种文字摘要，前端拿不到原图地址；引用一张图时
     * 卡片只能显示四个字，用户看不出引用了哪张。这个字段让前端能画缩略图。
     */
    private String replyToType;

    /** 被回复的是图片时，原图地址（已撤回/被删的消息不回，避免显示违规图） */
    private String replyToImg;

    /** 是否我发的（前端据此决定左右气泡，不用再比 id） */
    private Boolean self;

    /** 1 已撤回/被删 */
    private Integer deleted;

    /** 0 未删除 1 本人撤回 2 管理员删除（前端据此显示不同文案） */
    private Integer deletedBy;

    /** 客户端幂等 id：前端按它把本地乐观气泡替换成服务端真消息 */
    private String clientMsgId;

    private String createdAt;
}

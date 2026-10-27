package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群消息。{@code id} 是雪花（时间有序），直接当增量游标用 ——
 * WebSocket 断线补拉、HTTP 轮询兜底、上滑翻历史，三种场景共用一套 {@code sinceId/before} 语义。
 *
 * <p>{@code clientMsgId} 是幂等键：WS 断线重发、前端点两次、弱网重试都会撞
 * {@code uk_client}，服务端捕获后返回已存在的那条，绝不产生重复气泡。
 *
 * <p>{@code senderName/senderAvatar} 是快照，展示前由 Service 用最新资料覆盖
 * （与项目里 Post/Goods 的 refreshAuthors 同一套做法），避免旧昵称永远留在历史消息里。
 */
@Data
@TableName("t_group_message")
public class GroupMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long groupId;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    /** text / image / system */
    private String type;

    private String content;

    /** 0 无 1 @指定人 2 @所有人 */
    private Integer atType;

    /** JSON 数组字符串，atType=1 时最多 10 个 id */
    private String atUserIds;

    /** 被回复的消息 id，NULL = 不是回复 */
    private Long replyToId;

    /**
     * 被回复者昵称<b>快照</b>。不做 JOIN：一页 30 条消息若每条都回查原消息，
     * 翻历史会连锁扫描；而且原消息可能已撤回/归档，JOIN 直接查不到。
     */
    private String replyToName;

    /** 被回复内容摘要<b>快照</b>（最多 140 字），原消息撤回后引用块仍能看懂 */
    private String replyToText;

    /** 1 已撤回/被删（软删），聊天记录是纠纷证据，不做物理删除 */
    private Integer deleted;

    /** 删除来源：0 未删除 1 本人撤回 2 管理员删除（前端文案不同，一个 bit 分不出来） */
    private Integer deletedBy;

    private String clientMsgId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

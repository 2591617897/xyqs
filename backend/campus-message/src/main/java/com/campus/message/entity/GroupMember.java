package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群成员。一个 (groupId,userId) 一行，{@code uk_group_user} 唯一。
 *
 * <p>三个字段决定体验，改之前务必想清楚：
 * <ul>
 *   <li>{@code lastReadId} —— 已读游标。群聊<b>不能</b>给每条消息写万人已读行，
 *       未读数是 {@code COUNT(id > lastReadId AND sender != me)}，配 LIMIT 100 封顶；</li>
 *   <li>{@code mentionUnread} —— 只统计「@我」，写时 +1、读时清零；@所有人 不走这里；</li>
 *   <li>{@code status} —— 1 在群 / 0 被踢 / 2 主动退群。<b>0 和 2 都不参与自动入群</b>，
 *       否则用户退了又被拉回来，是最伤体验的 bug。</li>
 * </ul>
 */
@Data
@TableName("t_group_member")
public class GroupMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long groupId;

    private Long userId;

    /** 0 普通成员 1 群管理员（阶段一只享「夜静豁免 + 频率豁免 + @所有人」） */
    private Integer role;

    private Long lastReadId;

    private Integer mentionUnread;

    /** 0 仅 @ 提醒（默认） 1 全部消息 2 完全静音 */
    private Integer notifyMode;

    /** 是否已确认群规，0 时进群首屏强制弹引导层 */
    private Integer rulesAgreed;

    /** 1 在群 0 被踢 2 主动退群 */
    private Integer status;

    private LocalDateTime joinAt;

    private LocalDateTime leaveAt;
}

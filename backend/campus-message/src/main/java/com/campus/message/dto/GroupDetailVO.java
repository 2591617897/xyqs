package com.campus.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 群详情：群配置 + 我的状态。进群页拉一次，据此决定输入框是否可点、
 * 是否弹群规引导、群公告是否展示。
 */
@Data
public class GroupDetailVO {

    private String id;

    private String name;

    private String avatar;

    private String notice;

    private Integer memberCount;

    /** 1 正常 0 停用 */
    private Integer status;

    // ---------------- 我的状态 ----------------

    /** 0 普通成员 1 群管理员 */
    private Integer myRole;

    private Boolean rulesAgreed;

    private Integer notifyMode;

    /** 我在群里的已读游标 */
    private String lastReadId;

    private Integer unread;

    private Integer mentionUnread;

    private Boolean atAll;

    // ---------------- 发言管控（前端要据此置灰输入框并给出准确原因） ----------------

    /** 当前是否被禁言 */
    private Boolean muted;

    private String muteEndAt;

    private String muteReason;

    /** 当前是否处于夜间静默（已排除管理员豁免） */
    private Boolean nightMute;

    /** 夜间静默时段文案，如 "00:30-06:30" */
    private String nightRange;

    /** 当前生效的发言间隔（秒），管理员豁免时前端不看这个值 */
    private Integer rateGapSec;

    /** 每日发言上限 */
    private Integer rateDayMax;

    /** 我是否可以 @所有人（仅群管理员） */
    private Boolean atAllAllowed;

    /** 我是否还能发言（= 未禁言 且 非夜间静默 且 群未停用） */
    private Boolean canSend;

    /** 不能发言时的人话原因，直接展示在输入框上方 */
    private String blockReason;

    /** 未读的 @我 消息 id（点角标定位） */
    private List<String> unreadMentionIds;
}

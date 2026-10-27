package com.campus.message.dto;

import lombok.Data;

/**
 * 消息页的群卡片。一个用户最多一张（一所学校一个群）。
 */
@Data
public class GroupVO {

    private String id;

    private String name;

    private String avatar;

    private Integer memberCount;

    private String lastMsgText;

    private String lastMsgTime;

    /** 群消息未读数，>=100 时前端显示 99+ */
    private Integer unread;

    /** @我 + @所有人 的总数 */
    private Integer mentionUnread;

    /** 是否有未读的 @所有人（群卡片显示「@全体」角标） */
    private Boolean atAll;

    /** 我是否被禁言 */
    private Boolean muted;

    /** 当前是否处于夜间静默 */
    private Boolean nightMute;

    /** 是否已确认群规（false → 进群弹引导层） */
    private Boolean rulesAgreed;

    /** 0 仅@提醒 1 全部消息 2 完全静音 */
    private Integer notifyMode;

    /** 是否在群（false = 已退群/被踢，卡片显示「重新加入」） */
    private Boolean joined;

    /** 我的角色：0 普通 1 群管理员 */
    private Integer myRole;

    /** 群状态：1 正常 0 停用 */
    private Integer status;
}

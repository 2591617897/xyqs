package com.campus.message.dto;

import lombok.Data;

/**
 * 群成员（@ 选择器 / 群设置 / 后台成员 tab 共用）。
 * 由 {@code GroupMemberMapper#pageMembers} 直接映射，字段名与 SQL 别名对应。
 */
@Data
public class GroupMemberVO {

    private String id;

    private String groupId;

    private String userId;

    private String nickname;

    private String avatar;

    /** 0 未认证 1 审核中 2 已认证 */
    private Integer verifyStatus;

    /** 0 普通成员 1 群管理员 */
    private Integer role;

    /** 1 在群 0 被踢 2 主动退群 */
    private Integer status;

    /** 加入时间（已格式化） */
    private String joinAt;

    /** 当前是否被我（或后台）禁言中，列表里标一个「禁言中」小标签 */
    private Boolean muted;

    /** 我的已读游标（后台不需要，C 端不用，保留给排障） */
    private Long lastReadId;
}

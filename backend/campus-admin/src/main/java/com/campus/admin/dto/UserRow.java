package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户列表行。
 *
 * <p>blacklisted / muted 是投影位，真实判定读 t_user_blacklist 与 t_user_mute，
 * 这里冗余出来只是为了让列表页能直接筛选和打标，不用 N+1 查两张处置表。
 */
@Data
public class UserRow {

    private Long id;

    private String nickname;

    private String avatar;

    private String phone;

    private Integer gender;

    private Long schoolId;

    private String schoolName;

    /** 0未认证 1审核中 2已认证 3驳回 */
    private Integer verifyStatus;

    /** 1正常 0封禁 */
    private Integer status;

    /** 禁言到期时间投影（t_user.mute_until），NULL 表示未禁言 */
    private LocalDateTime muteUntil;

    private Integer fansCount;

    private Integer followCount;

    private LocalDateTime createdAt;

    // ---- 以下仅「用户详情」返回，列表查询不 select，前端不要依赖 ----

    private String bio;

    /** 实名信息：认证材料，仅后台可见 */
    private String realName;

    private String studentNo;

    private String verifyImg;

    private String verifyRemark;

    /** 是否在黑名单中（1是 0否） */
    private Integer blacklisted;

    /** 是否被禁言中（1是 0否） */
    private Integer muted;
}

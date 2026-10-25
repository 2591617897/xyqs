package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 宿主平台：wx 微信小程序 / qq QQ 小程序 */
    private String platform;

    /** 仅在 platform 内唯一，与 platform 组成联合唯一键 */
    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;

    /** 0未知 1男 2女 */
    private Integer gender;

    private String phone;

    private Long schoolId;

    private String studentNo;

    private String realName;

    /** 0未认证 1审核中 2已认证 3驳回 */
    private Integer verifyStatus;

    private String verifyImg;

    private String verifyRemark;

    private String bio;

    /** 粉丝数（关注关系计数器，greatest 防负数） */
    private Integer fansCount;

    /** 关注数（关注关系计数器，greatest 防负数） */
    private Integer followCount;

    /** 1正常 0封禁 */
    private Integer status;

    /**
     * 禁言到期时间（t_user.mute_until）。由后台 campus-admin 通过
     * /internal/admin/users/{id}/mute 写入，本服务只持有这一列、不负责维护事实
     * —— 真正的禁言记录是 t_user_mute。NULL 表示未禁言。
     */
    private LocalDateTime muteUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

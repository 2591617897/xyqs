package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实名认证审核流水。
 *
 * <p>最终态仍在 t_user.verify_status（0未认证 1审核中 2已认证 3驳回），
 * 这里只记录每次审核动作与驳回原因，供用户申诉与后台追溯。
 */
@Data
@TableName("t_user_verify_log")
public class UserVerifyLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String userName;

    private Long schoolId;

    private String schoolName;

    private String realName;

    private String studentNo;

    private String verifyImg;

    private Integer fromStatus;

    /** 2通过 3驳回 */
    private Integer toStatus;

    private String reason;

    private Long adminId;

    private String adminName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

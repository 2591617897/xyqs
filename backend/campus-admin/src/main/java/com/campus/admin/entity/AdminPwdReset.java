package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台找回密码的验证码申请记录。
 *
 * <p>只存 BCrypt 哈希，不存明文验证码；一行一条申请，既能做冷却/日限，
 * 也能保留「谁在什么时候从哪个 IP 申请过」的审计线索。
 */
@Data
@TableName("t_admin_pwd_reset")
public class AdminPwdReset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long adminId;

    /** 发送时快照：事后改绑邮箱不会把已发出的验证码转移到新地址 */
    private String email;

    private String codeHash;

    private LocalDateTime expireAt;

    /** 1=已用或已作废 */
    private Integer used;

    private Integer attemptCount;

    private String reqIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

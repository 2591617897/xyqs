package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户黑名单。与 t_user.status=0 联动：拉黑即封禁，解除即恢复。
 *
 * <p>status 同样用 NULL 表示已解除（理由见 {@link UserMute}），
 * 使「同一用户同时只有一条生效记录」且可反复拉黑/解除。
 */
@Data
@TableName("t_user_blacklist")
public class UserBlacklist {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String userName;

    private String reasonType;

    private String reason;

    /** 1生效中 NULL已解除 */
    private Integer status;

    private LocalDateTime releasedAt;

    private Long operatorId;

    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

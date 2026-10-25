package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 禁言记录（事实源）。
 *
 * <p><b>status 用 NULL 表示已解除，不是 0</b>：MySQL 唯一索引允许多个 NULL，
 * uk_user_scope_active(user_id, scope, status) 才能既保证「同一用户同一范围
 * 最多一条生效记录」，又允许多条历史解除记录共存（反复禁言不会撞键）。
 *
 * <p>C 端高频判断读的是投影列 t_user.mute_until，与本表在同一事务内同步。
 */
@Data
@TableName("t_user_mute")
public class UserMute {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 1全站 2发帖 3评论 4私信 */
    private Integer scope;

    /** 禁言时长（分钟），0 表示永久 */
    private Integer minutes;

    private String reasonType;

    private String reason;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 1生效中 NULL已解除 */
    private Integer status;

    private LocalDateTime releasedAt;

    /** 1人工 2到期自动 */
    private Integer releaseType;

    private Long operatorId;

    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

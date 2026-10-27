package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单人禁言。{@code endAt = null} 表示永久。
 *
 * <p>判定顺序固定为「单人禁言 → 夜间静音 → 频率 → 内容审核」，
 * 顺序错了会给用户错误的拒绝理由（明明被禁言三天，却提示"发言太频繁"）。
 */
@Data
@TableName("t_group_mute")
public class GroupMute {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long groupId;

    private Long userId;

    private LocalDateTime startAt;

    /** null = 永久 */
    private LocalDateTime endAt;

    private String reason;

    /** 0 = 系统 */
    private Long operatorId;

    /** 1 生效 0 已解除 */
    private Integer status;

    private LocalDateTime createdAt;
}

package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注关系（t_follow）。
 * 与 t_user 同库，由 campus-user 维护：关注/取消在同一事务内写关系行并更新两行计数器。
 */
@Data
@TableName("t_follow")
public class Follow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关注者（谁发起关注） */
    private Long followerId;

    /** 被关注者 */
    private Long followeeId;

    private LocalDateTime createdAt;
}

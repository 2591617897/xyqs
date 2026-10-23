package com.campus.common.social;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用点赞/收藏关系，targetType: GOODS / WALL / POST。
 * 放在 common，供商品、表白墙、帖子三个服务共用。
 */
@Data
@TableName("t_like")
public class Like {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String targetType;

    private Long targetId;

    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

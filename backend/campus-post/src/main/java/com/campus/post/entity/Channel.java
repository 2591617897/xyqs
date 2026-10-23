package com.campus.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_channel")
public class Channel {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String icon;

    private String description;

    /** null 表示全平台频道 */
    private Long schoolId;

    private Integer sort;

    private Integer postCount;

    private LocalDateTime createdAt;
}

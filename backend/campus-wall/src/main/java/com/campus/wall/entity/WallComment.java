package com.campus.wall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_wall_comment")
public class WallComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long postId;

    @JsonIgnore
    private Long userId;

    private String anonName;

    private String content;

    /** 逗号分隔，最多 3 张 */
    private String images;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean mine;
}

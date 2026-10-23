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
@TableName("t_wall_post")
public class WallPost {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仅入库用于风控追溯，绝不返回给客户端 */
    @JsonIgnore
    private Long userId;

    private Long schoolId;

    /** 对外展示的匿名昵称：匿名同学·037 */
    private String anonName;

    /** 匿名头像序号 1-8，前端据此上色 */
    private Integer anonAvatar;

    private String content;

    private String images;

    private Integer likeCount;

    private Integer commentCount;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean liked;

    /** 是否是当前用户发的（用于展示删除按钮），不暴露 user_id */
    @TableField(exist = false)
    private Boolean mine;
}

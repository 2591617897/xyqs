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

    /** 父评论 id，0 / null 表示直接评论；>0 表示对某条评论的回复 */
    private Long parentId;

    /** 被回复人的匿名昵称快照，用于「A 回复 B」展示 */
    private String replyToName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean mine;

    /** 该评论下的回复列表（仅顶层评论有值） */
    @TableField(exist = false)
    private List<WallComment> replies;

    /** 回复总数（可能大于返回的 replies 条数） */
    @TableField(exist = false)
    private Integer replyCount;
}

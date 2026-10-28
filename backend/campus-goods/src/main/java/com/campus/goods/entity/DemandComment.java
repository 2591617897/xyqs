package com.campus.goods.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求评论（含楼中楼回复）。
 *
 * <p>结构与 {@link GoodsComment} 保持一致：同一套两层树模型，
 * 前端因此可以直接复用帖子 / 闲置的评论区样式与交互。</p>
 */
@Data
@TableName("t_demand_comment")
public class DemandComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long demandId;

    private Long userId;

    /** 展示用昵称快照 */
    private String userName;

    private String userAvatar;

    private String content;

    /** 逗号分隔，最多 3 张 */
    private String images;

    /** 父评论 id，0 / null 表示直接评论；>0 表示对某条评论的回复 */
    private Long parentId;

    /** 被回复人的展示名快照，用于「A 回复 B」 */
    private String replyToName;

    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean mine;

    @TableField(exist = false)
    private Boolean liked;

    /** 该评论下的回复列表（仅顶层评论有值） */
    @TableField(exist = false)
    private List<DemandComment> replies;

    /** 回复总数 */
    @TableField(exist = false)
    private Integer replyCount;
}

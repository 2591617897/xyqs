package com.campus.post.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_post_comment")
public class PostComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long postId;

    private Long userId;

    private String userName;

    private String userAvatar;

    private String content;

    /** 逗号分隔，最多 3 张 */
    private String images;

    /** 父评论 id，0 / null 表示直接评论；>0 表示对某条评论的回复 */
    private Long parentId;

    /** 被回复人的展示名快照，用于「A 回复 B」 */
    private String replyToName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean mine;

    /** 该评论下的回复列表（仅顶层评论有值） */
    @TableField(exist = false)
    private List<PostComment> replies;

    /** 回复总数 */
    @TableField(exist = false)
    private Integer replyCount;

    /** 评论获赞数（t_like 里 target_type=COMMENT 的行数，实时聚合，不冗余计数列） */
    @TableField(exist = false)
    private Integer likeCount;

    /** 当前登录用户是否已给这条评论点赞 */
    @TableField(exist = false)
    private Boolean liked;

    /** 评论者等级 LV（由发帖数 + 评论数算出，见 UserLevel） */
    @TableField(exist = false)
    private Integer userLevel;

    /** 评论者所在省份（学校所在省；没绑学校时为空，前端不显示这一段） */
    @TableField(exist = false)
    private String userProvince;
}

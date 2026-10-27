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
@TableName("t_post")
public class Post {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long channelId;

    private Long userId;

    private Long schoolId;

    private String authorName;

    private String authorAvatar;

    private String title;

    private String content;

    private String images;

    private Integer likeCount;

    private Integer commentCount;

    private Integer status;

    /**
     * 可见范围：school 仅本校 / city 同市可见（默认 school）。
     * 见 {@link com.campus.common.content.Visibility}。
     */
    private String visibility;

    /**
     * 当前有效推广分。由 campus-promote 看广告后回写，并按 7 天线性衰减重算。
     * 列表第一排序键，必须落库。
     */
    private Integer promoteScore;

    /** 最晚一条推广的到期时间；0 分为 null */
    private LocalDateTime promoteExpireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private Boolean mine;

    @TableField(exist = false)
    private String channelName;

    @TableField(exist = false)
    private String channelIcon;
}

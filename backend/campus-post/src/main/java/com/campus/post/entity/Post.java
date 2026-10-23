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

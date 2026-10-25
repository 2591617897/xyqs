package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台帖子列表行（跨 t_post / t_channel / t_school 只读拼装）。
 */
@Data
public class PostRow {

    private Long id;

    private String title;

    private String content;

    private String images;

    private Long authorId;

    private String authorName;

    private String authorAvatar;

    private Long channelId;

    private String channelName;

    private Long schoolId;

    private String schoolName;

    private Integer likeCount;

    private Integer commentCount;

    /** 1正常 0隐藏 */
    private Integer status;

    private LocalDateTime createdAt;
}

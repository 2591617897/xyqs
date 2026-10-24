package com.campus.wall.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CommentReq {

    /** 允许为空：只发图片的评论也支持 */
    @Size(max = 500, message = "评论不超过 500 字")
    private String content;

    /** 最多 3 张图片 */
    @Size(max = 3, message = "最多上传 3 张图片")
    private List<String> images;

    /** 回复的父评论 id，空 / 0 表示直接评论 */
    private Long parentId;

    /**
     * 评论中 @ 的用户 id 列表。
     * 后端强校验：只能 @ 当前评论者已关注的人。
     */
    private List<Long> mentionUserIds;
}

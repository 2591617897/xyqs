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

    /**
     * 1 上线 / 0 下线。
     * 下线用软删而不是 DELETE：t_post.channel_id 有存量引用，
     * 删行会让历史帖子丢频道名；下线后 GET /channels 不返回，
     * 但帖子详情仍能按 id 解析出频道名。
     */
    private Integer status;

    private LocalDateTime createdAt;
}

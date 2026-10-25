package com.campus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片异步审核台账（t_media_check）。
 *
 * <p>为什么必须落一张表：微信 media_check_async 提交后只返回 trace_id，
 * 结论要等「消息推送」回调（可能几十秒后）才知道。没有这张表，
 * 回调回来时既不知道这张图属于哪条内容，也没法做处置。
 */
@Data
@TableName("t_media_check")
public class MediaCheck {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 微信返回的 trace_id，回调里用它反查，唯一 */
    private String traceId;

    private Long userId;

    /** POST / WALL / GOODS / *_COMMENT / MESSAGE */
    private String bizType;

    /** 业务主键，发布前拿不到时为 null */
    private Long bizId;

    private String mediaUrl;

    /** 1 图片 / 2 音频 */
    private Integer mediaType;

    /** 0 待审 / 1 通过 / 2 疑似 / 3 违规 / 4 送审失败 */
    private Integer status;

    /** 微信 result.suggest：pass / review / risky */
    private String suggest;

    /** 微信 result.label：违规类型编码，0 表示无 */
    private Integer label;

    /** 微信 errcode，非 0 表示送审/审查失败 */
    private Integer errcode;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

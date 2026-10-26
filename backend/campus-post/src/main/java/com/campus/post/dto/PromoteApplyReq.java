package com.campus.post.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推广分回写请求（promote 服务内部调用）。
 *
 * <p>和 campus-promote 里的同名 DTO 结构一致但各自独立：
 * 两个服务不互相依赖 module，跨服务只认 JSON 字段。
 */
@Data
public class PromoteApplyReq {

    /** 帖子 id */
    private Long targetId;

    /** 当前有效推广分 */
    private Integer score;

    /** 到期时间，0 分时为 null */
    private LocalDateTime expireAt;
}

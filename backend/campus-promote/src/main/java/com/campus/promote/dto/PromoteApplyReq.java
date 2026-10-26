package com.campus.promote.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推广分同步请求：promote 服务算完有效分后推给内容服务落库。
 */
@Data
public class PromoteApplyReq {

    /** 内容 id */
    private Long targetId;

    /** 当前有效推广分（已按剩余时间衰减） */
    private Integer score;

    /** 最晚一条推广的到期时间；score=0 时传 null */
    private LocalDateTime expireAt;
}

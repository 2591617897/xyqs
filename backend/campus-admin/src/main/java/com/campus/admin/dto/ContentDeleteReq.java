package com.campus.admin.dto;

import lombok.Data;

/**
 * 内容删除请求。
 *
 * <p>刻意不与 {@link DisposeReq} 复用：DisposeReq 的 status 带 {@code @NotNull}，
 * 删除场景没有 status，复用它会被校验层直接 400。
 */
@Data
public class ContentDeleteReq {

    /** 违规类型：色情低俗/暴力血腥/诈骗/广告骚扰/人身攻击/虚假信息/侵权/其他 */
    private String reasonType;

    /** 删除说明 */
    private String reason;
}

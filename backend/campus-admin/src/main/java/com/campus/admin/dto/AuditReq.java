package com.campus.admin.dto;

import lombok.Data;

/**
 * 通用「审核结论」入参：广告位/素材审核、实名认证审核共用。
 */
@Data
public class AuditReq {

    /** true 通过 false 驳回 */
    private Boolean pass;

    /** 驳回时必填的原因 / 审核意见 */
    private String reason;
}

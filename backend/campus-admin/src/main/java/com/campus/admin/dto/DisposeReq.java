package com.campus.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 内容处置入参：隐藏/恢复/下架。
 */
@Data
public class DisposeReq {

    /** 0隐藏/下架 1恢复 */
    @NotNull(message = "status 不能为空")
    private Integer status;

    /** 违规类型：色情低俗/暴力血腥/诈骗/广告骚扰/人身攻击/虚假信息/侵权/其他 */
    private String reasonType;

    /** 处置说明，隐藏时建议填写 */
    private String reason;
}

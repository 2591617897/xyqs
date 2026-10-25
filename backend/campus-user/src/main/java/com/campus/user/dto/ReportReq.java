package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ReportReq {

    /** 举报对象类型：POST WALL GOODS COMMENT USER MESSAGE FEEDBACK */
    @NotBlank(message = "请选择举报对象类型")
    private String targetType;

    /** 被举报对象 id；FEEDBACK（意见反馈）时为 null */
    private Long targetId;

    /** 举报原因分类：色情低俗/暴力血腥/诈骗/广告骚扰/人身攻击/虚假信息/侵权/其他 */
    @NotBlank(message = "请选择举报原因")
    private String reasonType;

    /** 补充说明 */
    private String reasonDesc;

    /** 举报凭证图片，逗号分隔 */
    private List<String> images;
}

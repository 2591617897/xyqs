package com.campus.admin.dto;

import lombok.Data;

@Data
public class ReportHandleReq {

    /** 1已处理 2已驳回 */
    private Integer status;

    /** 处理结果：下架内容/禁言作者/封禁作者/无违规/其他 */
    private String handleResult;

    /** 处理说明 */
    private String handleRemark;
}

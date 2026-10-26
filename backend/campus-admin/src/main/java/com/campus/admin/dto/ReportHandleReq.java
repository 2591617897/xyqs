package com.campus.admin.dto;

import lombok.Data;

@Data
public class ReportHandleReq {

    /**
     * 已废弃：最终状态由 handleResult 推导（无违规 → 2 已驳回，其余 → 1 已处理）。
     * 仅为兼容可能存在的旧调用方保留，后端 handle() 不会读它。
     */
    @Deprecated
    private Integer status;

    /** 处理结果：下架内容/禁言作者/封禁作者/无违规/其他 */
    private String handleResult;

    /** 处理说明 */
    private String handleRemark;
}

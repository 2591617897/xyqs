package com.campus.goods.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发布 / 编辑需求。
 *
 * <p>budget 允许为空 = 面议：求助类需求（搭把手、组队）常常没有报酬，
 * 强制填金额会把这类需求挡在门外，也会让模块看起来像「悬赏任务平台」——
 * 那是要额外资质的类目，本项目只做信息展示。
 */
@Data
public class DemandReq {

    /** 1求物 2求帮忙 */
    @NotNull(message = "请选择需求类型")
    @Min(value = 1, message = "需求类型不合法")
    @Max(value = 2, message = "需求类型不合法")
    private Integer type;

    @NotBlank(message = "请填写需求标题")
    @Size(max = 60, message = "标题不超过 60 字")
    private String title;

    /** 具体要求 */
    @Size(max = 2000, message = "要求不超过 2000 字")
    private String description;

    private Integer categoryId;

    /** 预算 / 报酬；为空表示面议 */
    @DecimalMin(value = "0", message = "预算不能为负数")
    private BigDecimal budget;

    @Size(max = 32, message = "期望时间不超过 32 字")
    private String expectTime;

    @Size(max = 64, message = "交接地点不超过 64 字")
    private String location;

    private List<String> images;

    /**
     * 可见范围：school 仅本校（默认）/ city 同市可见。
     * 不校验枚举直接归一化 —— 老客户端不带这个字段时按 school 处理，不该报 400。
     */
    @Size(max = 16, message = "可见范围参数不合法")
    private String visibility;
}

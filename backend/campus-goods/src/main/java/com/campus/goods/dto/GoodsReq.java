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

@Data
public class GoodsReq {

    @NotBlank(message = "请填写商品标题")
    @Size(max = 80, message = "标题不超过 80 字")
    private String title;

    @Size(max = 2000, message = "描述不超过 2000 字")
    private String description;

    @NotNull(message = "请填写价格")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer categoryId;

    /** 成色: 1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹 */
    @Min(value = 1, message = "成色不合法")
    @Max(value = 4, message = "成色不合法")
    private Integer conditionLevel;

    @Size(max = 20, message = "入手时间格式不正确")
    private String purchaseTime;

    @Size(max = 20, message = "使用频率格式不正确")
    private String usageFrequency;

    @Size(max = 20, message = "来源格式不正确")
    private String source;

    @Size(max = 20, message = "保修状态格式不正确")
    private String warranty;

    /** 是否支持面交试机 0否 1是 */
    @Min(value = 0, message = "面交试机参数不合法")
    @Max(value = 1, message = "面交试机参数不合法")
    private Integer supportTrial;

    private List<String> images;

    /** 1校内自提 2邮寄 */
    private Integer tradeType;

    private String location;
}

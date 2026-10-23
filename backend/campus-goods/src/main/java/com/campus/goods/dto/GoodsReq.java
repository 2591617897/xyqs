package com.campus.goods.dto;

import jakarta.validation.constraints.DecimalMin;
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

    private List<String> images;

    /** 1校内自提 2邮寄 */
    private Integer tradeType;

    private String location;
}

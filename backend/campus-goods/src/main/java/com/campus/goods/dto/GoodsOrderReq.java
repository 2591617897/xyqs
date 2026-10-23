package com.campus.goods.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GoodsOrderReq {

    @Size(max = 255, message = "留言不超过 255 字")
    private String message;

    @NotBlank(message = "请留下联系方式，方便卖家联系你")
    private String contact;
}

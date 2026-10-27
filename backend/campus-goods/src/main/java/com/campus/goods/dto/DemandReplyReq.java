package com.campus.goods.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DemandReplyReq {

    /** 我能提供什么 */
    @Size(max = 500, message = "留言不超过 500 字")
    private String message;

    /**
     * 联系方式（QQ / 微信 / 手机号），选填 —— 也可以只走私信。
     * 落库加密，见 DemandReply#contact。
     */
    @Size(max = 64, message = "联系方式不超过 64 字")
    private String contact;
}

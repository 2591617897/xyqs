package com.campus.message.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageReq {

    /** text / location / image / video */
    private String type;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}

package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 通用「附理由」入参。封禁、解封、拉黑、解除拉黑、重置密码等复用。
 */
@Data
public class ReasonReq {

    @NotBlank(message = "请填写操作理由")
    private String reason;
}

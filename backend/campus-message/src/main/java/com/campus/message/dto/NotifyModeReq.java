package com.campus.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 提醒模式：0 仅 @ 提醒（默认） 1 全部消息 2 完全静音 */
@Data
public class NotifyModeReq {

    @NotNull(message = "请指定提醒模式")
    private Integer notifyMode;
}

package com.campus.message.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 后台禁言请求。{@code hours = 0} 表示永久（end_at = NULL）。
 */
@Data
public class GroupMuteReq {

    @NotBlank(message = "请指定要禁言的用户")
    private String userId;

    /** 禁言小时数：0 = 永久 */
    private Integer hours;

    private String reason;
}

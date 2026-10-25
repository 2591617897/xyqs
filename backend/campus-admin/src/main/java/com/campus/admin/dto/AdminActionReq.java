package com.campus.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台对业务服务下发的处置指令（跨服务 internal 接口的通用入参）。
 *
 * <p>服务端用 Map 接收，不反向依赖 admin 的 dto——两侧各自演进，
 * 只要字段名（status / reason / operator）不变就不会互相打断。
 */
@Data
public class AdminActionReq {

    /** 目标状态：帖子/墙 0隐藏 1恢复；商品 0下架 1在售；用户 0封禁 1解封；消息 1删除 */
    @NotNull(message = "status 不能为空")
    private Integer status;

    /** 处置说明，写进对方服务的日志/快照 */
    private String reason;

    /** 操作人管理员ID */
    private Long operator;

    /** 操作人账号快照 */
    private String operatorName;
}

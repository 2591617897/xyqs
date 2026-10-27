package com.campus.message.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息提醒设置（GET /messages/settings 的响应体，PUT 的请求体）。
 *
 * <p>GET 时额外带上 {@link #templateIds} 与 {@link #wxPushAvailable}：
 * 订阅消息的模板 id 只能由后台/配置下发（前端硬编码会导致换模板就要发版），
 * 模板还没配时前端应当「只展示、不请求授权」，而不是拿空数组去调接口报错。
 */
@Data
public class MessageSettingsVO {

    /** 私信 */
    private Boolean notifyMessage = Boolean.TRUE;
    /** 赞与收藏 */
    private Boolean notifyLike = Boolean.TRUE;
    /** 新增粉丝 */
    private Boolean notifyFollow = Boolean.TRUE;
    /** 评论与@ */
    private Boolean notifyComment = Boolean.TRUE;
    /** 官方通知 */
    private Boolean notifySystem = Boolean.TRUE;

    /** 免打扰开关 */
    private Boolean dndEnabled = Boolean.FALSE;
    /** 免打扰开始 HH:mm */
    private String dndStart = "22:00";
    /** 免打扰结束 HH:mm，允许跨天 */
    private String dndEnd = "07:00";

    /** 微信订阅消息：0 未授权 / 1 已授权 / 2 已拒绝 */
    private Integer subscribeStatus = 0;

    /** 后端配置的订阅消息模板 id（空数组 = 还没配，前端不要发起授权） */
    private List<String> templateIds = new ArrayList<>();

    /** templateIds 非空即可用；是否已授权看 subscribeStatus */
    private Boolean wxPushAvailable = Boolean.FALSE;

    /** 是否已授权微信提醒（subscribeStatus == 1） */
    private Boolean wxPushOn = Boolean.FALSE;
}

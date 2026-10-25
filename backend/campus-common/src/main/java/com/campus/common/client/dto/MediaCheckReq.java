package com.campus.common.client.dto;

import java.util.List;

/**
 * 图片/音频异步内容安全送审入参（微信 media_check_async）。
 *
 * <p>为什么是异步：微信的媒体审核没有同步接口，提交后拿到 trace_id，
 * 结果要通过「消息推送」回调到服务端。所以这里只管提交，不返回结论。
 *
 * @param userId    提交者 id（用于反查 openid）
 * @param bizType   业务类型，取值与 t_content_audit.target_type 一致
 *                  （POST / WALL / GOODS / P_COMMENT / W_COMMENT / G_COMMENT / MESSAGE）
 * @param bizId     业务主键（发布时可能尚未落库，允许 null）
 * @param urls      图片地址（必须是公网可达的 http/https，微信侧要去下载）
 * @param mediaType 1 图片 / 2 音频（微信仅支持这两类，视频送审会失败）
 */
public record MediaCheckReq(Long userId, String bizType, Long bizId, List<String> urls, Integer mediaType) {
}

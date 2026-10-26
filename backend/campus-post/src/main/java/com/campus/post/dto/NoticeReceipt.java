package com.campus.post.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告弹窗回执（t_notice_receipt 的读模型）。
 *
 * <p>两种频次共用一行记录：
 * <ul>
 *   <li>{@code lastShownAt} —— 「每天一次」按它判断今天是否已经弹过；</li>
 *   <li>{@code confirmedAt} —— 「确认收到后不再显示」按它判断是否已被确认。</li>
 * </ul>
 * 分两列而不是一个「已读」标志，是因为同一条公告可能先按「每天一次」投放，
 * 运营改成「确认收到」后不该把已有的展示历史当作已确认。
 */
@Data
public class NoticeReceipt {

    private Long noticeId;

    private LocalDateTime firstShownAt;

    private LocalDateTime lastShownAt;

    private LocalDateTime confirmedAt;
}

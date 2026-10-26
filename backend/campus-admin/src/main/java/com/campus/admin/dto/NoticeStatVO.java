package com.campus.admin.dto;

import lombok.Data;

/**
 * 一条公告的弹窗触达统计。
 *
 * <p>两个数含义不同，别混着看：
 * <ul>
 *   <li><b>送达人数</b> —— t_notice_receipt 里去重后的人数（uk_user_notice 保证一人一行），
 *       即「弹窗真的弹给过多少人」；游客弹窗不落库，所以这个数只统计登录用户；</li>
 *   <li><b>确认人数</b> —— 其中点了「我知道了」的人。只有 popup_mode=3 才有意义，
 *       其它模式下确认只是顺手写一条，不代表送达率。</li>
 * </ul>
 */
@Data
public class NoticeStatVO {

    private Long noticeId;

    private Long shownCount;

    private Long confirmCount;
}

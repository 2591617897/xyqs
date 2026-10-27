package com.campus.message.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 群聊相关的时间格式化。
 *
 * <p>项目没有全局日期序列化规则（{@code JacksonConfig} 只处理 Long→String），
 * 直接把 {@link LocalDateTime} 丢给前端会得到 {@code 2026-10-27T13:20:11.123}，
 * 小程序端 {@code new Date()} 在 iOS 上解析带 T/毫秒的串会得到 Invalid Date。
 * 所以群聊所有对外时间一律在这里格式化成固定字符串。
 */
public final class GroupTime {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private GroupTime() {
    }

    /** 消息时间：精确到秒，聊天页展示用 */
    public static String msg(LocalDateTime t) {
        return t == null ? "" : t.format(SECOND);
    }

    /** 列表时间：精确到分，消息页卡片摘要用 */
    public static String brief(LocalDateTime t) {
        return t == null ? "" : t.format(MINUTE);
    }

    /** 只有时分：禁言到几点、夜间静音时段用 */
    public static String clock(LocalDateTime t) {
        return t == null ? "" : t.format(CLOCK);
    }
}

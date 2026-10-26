package com.campus.promote.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推广规则配置。所有阈值集中在这里，避免散落成魔法数字。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.promote")
public class PromoteProperties {

    /** 微信激励视频广告位 ID */
    private String adUnitId = "";

    /** 看一次给的基础分（衰减前） */
    private int baseScore = 100;

    /** 权重有效期（天） */
    private int decayDays = 7;

    /** 最短观看时长（秒） */
    private int minWatchSeconds = 15;

    /** 单个内容每天最多推广次数 */
    private int dailyPerTarget = 5;

    /** 单个用户每天最多推广次数 */
    private int dailyPerUser = 20;

    /** 凭证有效期（分钟） */
    private int tokenTtlMinutes = 10;

    /** 联调模式（跳过真实广告） */
    private boolean mockAd = false;

    /** 权重总有效秒数 */
    public long decaySeconds() {
        return decayDays <= 0 ? 7L * 86400L : (long) decayDays * 86400L;
    }
}

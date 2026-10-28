package com.campus.user.service;

import com.campus.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上传配额（按用户、按天）。
 *
 * <h3>为什么需要</h3>
 * <p>上传接口原来只做了「单文件大小 + 魔数校验」，这两项都做得不错，但缺一件事：
 * <b>没有任何「每人每天能传多少」的限制</b>。一个已登录账号可以无限次上传 50MB 视频，
 * 直到把磁盘写满 —— 而这台服务器上还跑着别的项目，磁盘写满会是跨项目的故障
 * （第三轮审计 P1-3）。
 *
 * <h3>阈值</h3>
 * <ul>
 *   <li>数量：每天 60 个。正常同学一天发几条闲置（每条 3~6 张图）远到不了；</li>
 *   <li>总量：每天 200MB。图片经小程序端压到 1MB 内，视频聊天才是大头，
 *       200MB 够发 4 条 50MB 视频，也够 200 张图。</li>
 * </ul>
 * 两个阈值都做成配置项（{@code campus.upload.daily-max-*}），运营可随磁盘水位调整。
 *
 * <h3>为什么是进程内</h3>
 * <p>与 {@code LoginRateLimiter} / {@code WriteRatePolicy} 同一取舍：本项目没有 Redis，
 * 单实例部署够用。多实例时实际额度会放大到「实例数 × 阈值」，届时换 Redis 的 INCR + EXPIRE。
 *
 * <p><b>只统计成功落盘的文件</b>：校验失败 / 类型不符的请求不该消耗用户的额度。
 */
@Slf4j
@Component
public class UploadQuotaService {

    /** 每天允许上传的文件个数 */
    @Value("${campus.upload.daily-max-count:60}")
    private int dailyMaxCount;

    /** 每天允许上传的总字节数（默认 200MB） */
    @Value("${campus.upload.daily-max-bytes:209715200}")
    private long dailyMaxBytes;

    private final Map<Long, Stat> stats = new ConcurrentHashMap<>();

    /**
     * 落盘前校验额度；超限抛 429。
     *
     * @param userId 当前用户
     * @param size   本次文件字节数
     */
    public void assertAllowed(Long userId, long size) {
        if (userId == null) {
            return;
        }
        Stat s = statOf(userId);
        synchronized (s) {
            if (s.count >= dailyMaxCount) {
                log.warn("[security] 上传额度超限（数量）userId={} count={}", userId, s.count);
                throw BizException.tooManyRequests(
                        "今天上传的文件数量已达上限（" + dailyMaxCount + " 个），明天再来吧");
            }
            if (s.bytes + size > dailyMaxBytes) {
                log.warn("[security] 上传额度超限（容量）userId={} used={}MB add={}KB",
                        userId, s.bytes / 1024 / 1024, size / 1024);
                throw BizException.tooManyRequests(
                        "今天上传的总容量已达上限（" + (dailyMaxBytes / 1024 / 1024) + "MB），明天再来吧");
            }
        }
    }

    /** 落盘成功后记账。只有真正存下来的文件才计数。 */
    public void record(Long userId, long size) {
        if (userId == null) {
            return;
        }
        Stat s = statOf(userId);
        synchronized (s) {
            s.count++;
            s.bytes += size;
        }
    }

    /**
     * 取（并按需重置）某用户当天的统计。
     *
     * <p>跨天自动归零，不需要定时任务：日期一变，下一次访问就重置。
     */
    private Stat statOf(Long userId) {
        LocalDate today = LocalDate.now();
        Stat s = stats.computeIfAbsent(userId, k -> new Stat());
        synchronized (s) {
            if (!today.equals(s.day)) {
                s.day = today;
                s.count = 0;
                s.bytes = 0;
            }
        }
        return s;
    }

    /** 单用户当日统计 */
    private static final class Stat {
        private LocalDate day;
        private int count;
        private long bytes;
    }
}

package com.campus.promote.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.promote.client.GoodsPromoteClient;
import com.campus.promote.client.PostPromoteClient;
import com.campus.promote.client.WallPromoteClient;
import com.campus.promote.config.PromoteProperties;
import com.campus.promote.dto.PromoteApplyReq;
import com.campus.promote.entity.PromoteRecord;
import com.campus.promote.mapper.PromoteRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 推广核心服务。
 *
 * <p><b>为什么是「签发凭证 → 结算」两步，而不是看完广告直接加一次分</b>：
 * 看完广告这件事只有客户端知道，服务端无从验证。直接给一个「加一分」的接口，
 * 任何人抓包就能无限刷到榜首。两步走之后服务端至少能校验三件事——
 * 凭证是一次性的、凭证是本人申请的、从签发到结算的时间够看完整段视频。
 * 客户端当然仍能伪造「看完了」，但伪造成本从「一次请求」变成「一次请求 + 等待 15 秒」，
 * 再叠加每日次数上限，对校园小程序的量级已经足够。
 *
 * <p><b>分数为什么落库而不是读时聚合</b>：列表要按推广分排序，
 * 读时聚合意味着要么在 SQL 里 join 流水表算衰减（跑不动），
 * 要么把候选集全捞出来在内存里排（分页就废了）。
 * 所以内容表上冗余 promote_score，由 {@link #refreshExpired()} 定时重算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoteService {

    public record IssueVO(String token, String adUnitId, int minWatchSeconds, boolean mockAd,
                          int ttlMinutes, int baseScore, int decayDays) {
    }

    public record CompleteVO(int score, int delta, LocalDateTime expireAt) {
    }

    public record QuotaVO(int score, LocalDateTime expireAt, int remainTarget, int remainUser,
                          int dailyPerTarget, int dailyPerUser, int baseScore, int decayDays,
                          int minWatchSeconds, boolean mockAd, boolean hasAdUnit) {
    }

    private final PromoteRecordMapper recordMapper;
    private final PromoteProperties props;
    private final GoodsPromoteClient goodsClient;
    private final PostPromoteClient postClient;
    private final WallPromoteClient wallClient;

    /**
     * 签发看广告凭证。
     *
     * <p>只登记意图、不加分。真正加分在 {@link #complete}。
     */
    @Transactional(rollbackFor = Exception.class)
    public IssueVO issue(Long userId, String targetType, Long targetId) {
        requireTarget(targetType, targetId);
        requireOwner(userId, targetType, targetId);

        LocalDateTime now = LocalDateTime.now();
        checkQuota(userId, targetType, targetId, now);

        PromoteRecord rec = new PromoteRecord();
        rec.setUserId(userId);
        rec.setTargetType(targetType);
        rec.setTargetId(targetId);
        rec.setNonce(UUID.randomUUID().toString().replace("-", ""));
        rec.setWeight(0);
        rec.setStatus(PromoteRecord.STATUS_PENDING);
        rec.setAdUnitId(props.getAdUnitId());
        rec.setIssuedAt(now);
        rec.setCreatedAt(now);
        recordMapper.insert(rec);

        return new IssueVO(rec.getNonce(), props.getAdUnitId(), props.getMinWatchSeconds(),
                props.isMockAd(), props.getTokenTtlMinutes(), props.getBaseScore(), props.getDecayDays());
    }

    /**
     * 结算：看完整段视频后凭一次性凭证加分。
     */
    @Transactional(rollbackFor = Exception.class)
    public CompleteVO complete(Long userId, String token) {
        if (!StringUtils.hasText(token)) {
            throw BizException.of("推广凭证无效");
        }
        PromoteRecord rec = recordMapper.selectOne(Wrappers.<PromoteRecord>lambdaQuery()
                .eq(PromoteRecord::getNonce, token.trim()));
        if (rec == null) {
            throw BizException.of("推广凭证无效");
        }
        if (!rec.getUserId().equals(userId)) {
            throw BizException.forbidden("只能结算自己发起的推广");
        }
        if (rec.getStatus() == PromoteRecord.STATUS_DONE) {
            throw BizException.of("这次推广已经记过一次了");
        }
        if (rec.getStatus() == PromoteRecord.STATUS_VOID) {
            throw BizException.of("推广凭证已作废，请重新发起");
        }

        LocalDateTime now = LocalDateTime.now();
        if (rec.getIssuedAt().plusMinutes(Math.max(1, props.getTokenTtlMinutes())).isBefore(now)) {
            voidRecord(rec.getId());
            throw BizException.of("推广凭证已超时，请重新发起");
        }
        long watched = Duration.between(rec.getIssuedAt(), now).getSeconds();
        if (watched < props.getMinWatchSeconds()) {
            throw BizException.of("视频还没看完，完整观看后才能获得推广");
        }

        // 带 status=0 条件的更新：并发重复结算时后到者影响 0 行，直接判重返回，
        // 否则同一次观看会加两次分。
        int updated = recordMapper.update(null, Wrappers.<PromoteRecord>lambdaUpdate()
                .set(PromoteRecord::getStatus, PromoteRecord.STATUS_DONE)
                .set(PromoteRecord::getWeight, props.getBaseScore())
                .set(PromoteRecord::getFinishAt, now)
                .set(PromoteRecord::getExpireAt, now.plusSeconds(props.decaySeconds()))
                .eq(PromoteRecord::getId, rec.getId())
                .eq(PromoteRecord::getStatus, PromoteRecord.STATUS_PENDING));
        if (updated == 0) {
            throw BizException.of("这次推广已经记过一次了");
        }

        int score = effectiveScore(rec.getTargetType(), rec.getTargetId(), now);
        LocalDateTime expireAt = latestExpire(rec.getTargetType(), rec.getTargetId(), now);
        syncScore(rec.getTargetType(), rec.getTargetId(), score, expireAt);
        return new CompleteVO(score, props.getBaseScore(), expireAt);
    }

    /**
     * 作废凭证（广告加载失败 / 用户中途放弃）。
     *
     * <p>作废后不占当日配额，否则用户手滑点一次推广，今天就少一次机会。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        PromoteRecord rec = recordMapper.selectOne(Wrappers.<PromoteRecord>lambdaQuery()
                .eq(PromoteRecord::getNonce, token.trim())
                .eq(PromoteRecord::getUserId, userId));
        if (rec == null || rec.getStatus() != PromoteRecord.STATUS_PENDING) {
            return;
        }
        voidRecord(rec.getId());
    }

    /** 查询当前推广状态与今日剩余次数 */
    public QuotaVO quota(Long userId, String targetType, Long targetId) {
        requireTarget(targetType, targetId);
        requireOwner(userId, targetType, targetId);
        LocalDateTime now = LocalDateTime.now();
        int usedTarget = usedCount(Wrappers.<PromoteRecord>lambdaQuery()
                .eq(PromoteRecord::getTargetType, targetType)
                .eq(PromoteRecord::getTargetId, targetId), now);
        int usedUser = usedCount(Wrappers.<PromoteRecord>lambdaQuery()
                .eq(PromoteRecord::getUserId, userId), now);
        return new QuotaVO(
                effectiveScore(targetType, targetId, now),
                latestExpire(targetType, targetId, now),
                Math.max(0, props.getDailyPerTarget() - usedTarget),
                Math.max(0, props.getDailyPerUser() - usedUser),
                props.getDailyPerTarget(),
                props.getDailyPerUser(),
                props.getBaseScore(),
                props.getDecayDays(),
                props.getMinWatchSeconds(),
                props.isMockAd(),
                StringUtils.hasText(props.getAdUnitId()));
    }

    /**
     * 衰减重算：把「近 8 天内还有推广记录」的内容重新算一遍有效分并回写。
     *
     * <p>幂等，失败只记 warn —— 下一次调度会补上，最坏情况是排序延迟一个周期。
     */
    public int refreshExpired() {
        LocalDateTime now = LocalDateTime.now();
        // 只扫「还没过期超过 1 天」的：更老的记录上一次重算时已经算成 0 写回去了
        LocalDateTime since = now.minusDays(1);
        List<PromoteRecord> records;
        try {
            records = recordMapper.selectToRefresh(since);
        } catch (Exception e) {
            log.warn("[promote] 衰减任务查询失败: {}", e.getMessage());
            return 0;
        }
        if (records.isEmpty()) {
            return 0;
        }
        int done = 0;
        for (PromoteRecord.Target ref : records.stream()
                .map(r -> new PromoteRecord.Target(r.getTargetType(), r.getTargetId()))
                .distinct()
                .toList()) {
            int score = effectiveScore(ref.targetType(), ref.targetId(), now);
            LocalDateTime expireAt = latestExpire(ref.targetType(), ref.targetId(), now);
            try {
                syncScore(ref.targetType(), ref.targetId(), score, expireAt);
                done++;
            } catch (Exception e) {
                log.warn("[promote] 衰减回写失败 {} {}: {}", ref.targetType(), ref.targetId(), e.getMessage());
            }
        }
        return done;
    }

    /**
     * 有效分：每条记录按「剩余时间 / 总有效期」线性折算后求和。
     *
     * <p>线性衰减而不是到期一刀切，是为了让「刚推广」和「快过期」在排序上有连续差异，
     * 也避免同一批内容在到期瞬间集体掉名次、列表一整页重排。
     */
    public int effectiveScore(String targetType, Long targetId, LocalDateTime now) {
        List<PromoteRecord> list = recordMapper.selectEffective(targetType, targetId, now);
        if (list.isEmpty()) {
            return 0;
        }
        long total = props.decaySeconds();
        int sum = 0;
        for (PromoteRecord r : list) {
            long remain = Duration.between(now, r.getExpireAt()).getSeconds();
            if (remain <= 0) {
                continue;
            }
            int w = r.getWeight() == null ? 0 : r.getWeight();
            sum += (int) Math.round(w * (remain / (double) total));
        }
        return sum;
    }

    private LocalDateTime latestExpire(String targetType, Long targetId, LocalDateTime now) {
        return recordMapper.selectEffective(targetType, targetId, now).stream()
                .map(PromoteRecord::getExpireAt)
                .filter(e -> e != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private void syncScore(String targetType, Long targetId, int score, LocalDateTime expireAt) {
        PromoteApplyReq req = new PromoteApplyReq();
        req.setTargetId(targetId);
        req.setScore(score);
        req.setExpireAt(score > 0 ? expireAt : null);
        R<Void> res = switch (targetType) {
            case PromoteRecord.GOODS -> goodsClient.applyPromote(req);
            case PromoteRecord.POST -> postClient.applyPromote(req);
            case PromoteRecord.WALL -> wallClient.applyPromote(req);
            default -> throw BizException.of("不支持推广的内容类型");
        };
        if (res == null || res.getCode() != 0) {
            throw BizException.of("推广分同步失败，请稍后重试");
        }
    }

    private void voidRecord(Long id) {
        recordMapper.update(null, Wrappers.<PromoteRecord>lambdaUpdate()
                .set(PromoteRecord::getStatus, PromoteRecord.STATUS_VOID)
                .eq(PromoteRecord::getId, id)
                .eq(PromoteRecord::getStatus, PromoteRecord.STATUS_PENDING));
    }

    /**
     * 已消耗次数：已结算的 + 仍在有效期内的待观看凭证。
     * 已作废的不算，否则用户手滑点一次就永久少一次机会。
     */
    private int usedCount(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PromoteRecord> base,
                          LocalDateTime now) {
        long cnt = recordMapper.selectCount(base
                .ge(PromoteRecord::getCreatedAt, now.toLocalDate().atStartOfDay())
                .and(q -> q.eq(PromoteRecord::getStatus, PromoteRecord.STATUS_DONE)
                        .or(w -> w.eq(PromoteRecord::getStatus, PromoteRecord.STATUS_PENDING)
                                .ge(PromoteRecord::getIssuedAt,
                                        now.minusMinutes(Math.max(1, props.getTokenTtlMinutes()))))));
        return (int) cnt;
    }

    private void checkQuota(Long userId, String targetType, Long targetId, LocalDateTime now) {
        int usedTarget = usedCount(Wrappers.<PromoteRecord>lambdaQuery()
                .eq(PromoteRecord::getTargetType, targetType)
                .eq(PromoteRecord::getTargetId, targetId), now);
        if (usedTarget >= props.getDailyPerTarget()) {
            throw BizException.tooManyRequests("这条内容今天已经推广 " + props.getDailyPerTarget() + " 次了，明天再来");
        }
        int usedUser = usedCount(Wrappers.<PromoteRecord>lambdaQuery()
                .eq(PromoteRecord::getUserId, userId), now);
        if (usedUser >= props.getDailyPerUser()) {
            throw BizException.tooManyRequests("今天推广次数用完了，明天再来");
        }
    }

    private void requireTarget(String targetType, Long targetId) {
        if (!PromoteRecord.supported(targetType)) {
            throw BizException.of("不支持推广的内容类型");
        }
        if (targetId == null || targetId <= 0) {
            throw BizException.of("内容不存在");
        }
    }

    /**
     * 归属校验：promote 服务不持有内容表，向各内容服务问一次作者。
     * 拿不到（内容已删除 / 服务不可用）按失败处理，不给陌生人推广别人的内容留下口子。
     */
    private void requireOwner(Long userId, String targetType, Long targetId) {
        Long owner;
        try {
            R<Long> res = switch (targetType) {
                case PromoteRecord.GOODS -> goodsClient.owner(targetId);
                case PromoteRecord.POST -> postClient.owner(targetId);
                case PromoteRecord.WALL -> wallClient.owner(targetId);
                default -> throw BizException.of("不支持推广的内容类型");
            };
            owner = res == null || res.getCode() != 0 ? null : res.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[promote] 归属查询失败 {} {}: {}", targetType, targetId, e.getMessage());
            throw BizException.of("内容服务暂不可用，请稍后重试");
        }
        if (owner == null) {
            throw BizException.notFound("内容不存在或已删除");
        }
        if (!owner.equals(userId)) {
            throw BizException.forbidden("只能推广自己发布的内容");
        }
    }
}

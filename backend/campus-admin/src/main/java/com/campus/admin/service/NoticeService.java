package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.config.AdminContext;
import com.campus.admin.dto.NoticeStatVO;
import com.campus.admin.entity.AdminNotice;
import com.campus.admin.mapper.AdminNoticeMapper;
import com.campus.common.search.SearchKit;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 通知公告。草稿 → 已发布 → 已下线。
 *
 * <p>发布时补 publish_at 与发布人快照：公告是要被追溯的，
 * 半年后没人记得某条公告是谁发的，只能靠这两列。
 *
 * <p><b>时间窗是这套功能的主线</b>：
 * <ul>
 *   <li>{@code publish_at} 在未来 = 预约发布，C 端到点自动出现（不需要定时任务推）；</li>
 *   <li>{@code offline_at} 在未来 = 定时下线，C 端立刻按它过滤，
 *       后台状态由 {@code NoticeExpireJob} 每 5 分钟翻成「已下线」。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final AdminNoticeMapper noticeMapper;

    public R.Page<AdminNotice> pageNotices(String keyword, Integer status, Integer popupMode,
                                           Integer targetType, long page, long size) {
        LambdaQueryWrapper<AdminNotice> qw = Wrappers.<AdminNotice>lambdaQuery()
                .eq(status != null, AdminNotice::getStatus, status)
                // popupMode=0 是有效筛选值（筛「不弹窗」），所以只能用 null 判断
                .eq(popupMode != null, AdminNotice::getPopupMode, popupMode)
                .eq(targetType != null, AdminNotice::getTargetType, targetType)
                .orderByDesc(AdminNotice::getLevel)
                .orderByDesc(AdminNotice::getId);
        SearchKit.andAllWords(qw, keyword, AdminNotice::getTitle);
        IPage<AdminNotice> p = noticeMapper.selectPage(new Page<>(page, size), qw);
        fillStats(p.getRecords());
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    /**
     * 给本页公告补上弹窗送达/确认人数。
     *
     * <p>一次 IN 聚合，不在循环里逐条 count；没有回执的公告补 0，
     * 前端就不用处理 undefined（否则表格会出现空白格，看起来像加载失败）。
     */
    private void fillStats(List<AdminNotice> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(AdminNotice::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, NoticeStatVO> stats = noticeMapper.selectReceiptStats(ids).stream()
                .collect(Collectors.toMap(NoticeStatVO::getNoticeId, v -> v, (a, b) -> a));
        for (AdminNotice n : records) {
            NoticeStatVO s = stats.get(n.getId());
            n.setShownCount(s == null || s.getShownCount() == null ? 0L : s.getShownCount());
            n.setConfirmCount(s == null || s.getConfirmCount() == null ? 0L : s.getConfirmCount());
        }
    }

    public AdminNotice detail(Long id) {
        AdminNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw BizException.notFound("公告不存在");
        }
        return notice;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminNotice notice) {
        if (notice.getTitle() == null || notice.getTitle().isBlank()) {
            throw BizException.of("标题不能为空");
        }
        if (notice.getContent() == null || notice.getContent().isBlank()) {
            throw BizException.of("正文不能为空");
        }
        notice.setStatus(notice.getStatus() == null ? 0 : notice.getStatus());
        notice.setType(notice.getType() == null ? 1 : notice.getType());
        notice.setTargetType(notice.getTargetType() == null ? 1 : notice.getTargetType());
        notice.setLevel(notice.getLevel() == null ? 1 : notice.getLevel());
        notice.setPopupMode(normalizePopupMode(notice.getPopupMode()));
        checkPopupTarget(notice.getTargetType(), notice.getPopupMode());
        checkWindow(notice.getPublishAt(), notice.getOfflineAt());
        if (Integer.valueOf(1).equals(notice.getStatus())) {
            // 已经填了未来时间就是「预约发布」，不能覆盖成 now，否则预约直接失效
            if (notice.getPublishAt() == null) {
                notice.setPublishAt(LocalDateTime.now());
            }
            notice.setPublisherId(AdminContext.adminId());
            notice.setPublisherName(AdminContext.username());
        }
        noticeMapper.insert(notice);
        return notice.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminNotice req) {
        AdminNotice exist = detail(id);
        Integer targetType = req.getTargetType() == null ? exist.getTargetType() : req.getTargetType();
        Integer popupMode = normalizePopupMode(req.getPopupMode());
        checkPopupTarget(targetType, popupMode);
        checkWindow(req.getPublishAt(), req.getOfflineAt());
        noticeMapper.update(null, Wrappers.<AdminNotice>lambdaUpdate()
                .eq(AdminNotice::getId, id)
                .set(req.getTitle() != null, AdminNotice::getTitle, req.getTitle())
                .set(req.getContent() != null, AdminNotice::getContent, req.getContent())
                .set(req.getType() != null, AdminNotice::getType, req.getType())
                .set(AdminNotice::getTargetType, targetType)
                // 投放对象从「指定校区」改回全体时必须能写回 NULL：
                // updateById 会跳过 null 字段，schoolId 会永久残留，只能显式 set
                .set(AdminNotice::getSchoolId,
                        Integer.valueOf(3).equals(targetType) ? req.getSchoolId() : null)
                .set(req.getLevel() != null, AdminNotice::getLevel, req.getLevel())
                .set(AdminNotice::getPopupMode, popupMode)
                // 两个时间都允许被清空：清空 publishAt = 立即发布，
                // 清空 offlineAt = 取消定时下线。表单每次都完整回填，不存在「漏传被误清」
                .set(AdminNotice::getPublishAt, req.getPublishAt())
                .set(AdminNotice::getOfflineAt, req.getOfflineAt()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (noticeMapper.deleteById(id) == 0) {
            throw BizException.notFound("公告不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        AdminNotice exist = detail(id);
        if (Integer.valueOf(1).equals(exist.getStatus())) {
            throw BizException.of("公告已发布");
        }
        checkPopupTarget(exist.getTargetType(), exist.getPopupMode());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishAt = exist.getPublishAt() == null ? now : exist.getPublishAt();
        LocalDateTime offlineAt = exist.getOfflineAt();
        // 只清「已经过去的下线时间」：那是之前手动下线留下的痕迹，
        // 不清的话公告重新发布后会被 C 端当成已过期（后台显示已发布、小程序永远看不到）。
        // 未来时间代表运营设的定时下线，必须保留，否则一发布就把预约下线弄丢了。
        if (offlineAt != null && !offlineAt.isAfter(now)) {
            offlineAt = null;
        }
        checkWindow(publishAt, offlineAt);
        noticeMapper.update(null, Wrappers.<AdminNotice>lambdaUpdate()
                .eq(AdminNotice::getId, id)
                .set(AdminNotice::getStatus, 1)
                .set(AdminNotice::getPublishAt, publishAt)
                .set(AdminNotice::getOfflineAt, offlineAt)
                .set(AdminNotice::getPublisherId, AdminContext.adminId())
                .set(AdminNotice::getPublisherName, AdminContext.username()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        detail(id);
        noticeMapper.update(null, Wrappers.<AdminNotice>lambdaUpdate()
                .eq(AdminNotice::getId, id)
                .set(AdminNotice::getStatus, 2)
                .set(AdminNotice::getOfflineAt, LocalDateTime.now()));
    }

    /**
     * 定时下线：把已发布且过了下线时间的公告翻成「已下线」。
     *
     * <p>只改 status，<b>不覆盖 offline_at</b>：那一列是运营填的计划下线时间，
     * 事后要能回答「这条公告是什么时候停的」，写成任务执行时刻就失真了。
     *
     * <p>C 端本来就用 offline_at 过滤，所以这里晚跑几分钟不会让过期公告多露出，
     * 这个任务只负责让后台状态和事实一致。
     *
     * @return 本次下线的条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireDue() {
        return noticeMapper.update(null, Wrappers.<AdminNotice>lambdaUpdate()
                .eq(AdminNotice::getStatus, 1)
                .isNotNull(AdminNotice::getOfflineAt)
                .le(AdminNotice::getOfflineAt, LocalDateTime.now())
                .set(AdminNotice::getStatus, 2));
    }

    /** 弹窗频次只有 0~3，越界一律按「不弹窗」处理，避免脏值把 C 端弹窗逻辑打乱 */
    private Integer normalizePopupMode(Integer popupMode) {
        if (popupMode == null || popupMode < 0 || popupMode > 3) {
            return 0;
        }
        return popupMode;
    }

    /**
     * 投放给「后台管理员」的公告根本不会进小程序，开弹窗是没有意义的配置，
     * 直接拒绝比让运营在后台配完再困惑更省事。
     */
    private void checkPopupTarget(Integer targetType, Integer popupMode) {
        if (popupMode != null && popupMode > 0 && Integer.valueOf(1).equals(targetType)) {
            throw BizException.of("投放对象为「后台管理员」时不能开启小程序弹窗");
        }
    }

    /**
     * 时间窗校验：自动下线必须晚于发布时间，否则公告一发布就是过期状态。
     * 两个都不填是合法的（立即发布 + 永不下线）。
     */
    private void checkWindow(LocalDateTime publishAt, LocalDateTime offlineAt) {
        if (publishAt != null && offlineAt != null && !offlineAt.isAfter(publishAt)) {
            throw BizException.of("自动下线时间必须晚于发布时间");
        }
    }
}

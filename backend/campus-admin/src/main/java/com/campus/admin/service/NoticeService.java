package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.config.AdminContext;
import com.campus.admin.entity.AdminNotice;
import com.campus.admin.mapper.AdminNoticeMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 通知公告。草稿 → 已发布 → 已下线。
 *
 * <p>发布时补 publish_at 与发布人快照：公告是要被追溯的，
 * 半年后没人记得某条公告是谁发的，只能靠这两列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final AdminNoticeMapper noticeMapper;

    public R.Page<AdminNotice> pageNotices(String keyword, Integer status, Integer targetType, long page, long size) {
        IPage<AdminNotice> p = noticeMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdminNotice>lambdaQuery()
                        .like(keyword != null && !keyword.isBlank(), AdminNotice::getTitle, keyword)
                        .eq(status != null, AdminNotice::getStatus, status)
                        .eq(targetType != null, AdminNotice::getTargetType, targetType)
                        .orderByDesc(AdminNotice::getLevel)
                        .orderByDesc(AdminNotice::getId));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
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
        if (Integer.valueOf(1).equals(notice.getStatus())) {
            notice.setPublishAt(LocalDateTime.now());
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
                // 发布时间留空表示「立即发布」，不能因为编辑表单没带这一项就被清掉
                .set(req.getPublishAt() != null, AdminNotice::getPublishAt, req.getPublishAt()));
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
        noticeMapper.update(null, Wrappers.<AdminNotice>lambdaUpdate()
                .eq(AdminNotice::getId, id)
                .set(AdminNotice::getStatus, 1)
                .set(AdminNotice::getPublishAt, exist.getPublishAt() == null ? LocalDateTime.now() : exist.getPublishAt())
                // 必须清空 offline_at：C 端用「offline_at IS NULL OR offline_at >= NOW()」判过期，
                // 下线时写入的时间会一直留着，重新发布后公告在后台显示「已发布」却永远不会出现在小程序里。
                .set(AdminNotice::getOfflineAt, null)
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
}

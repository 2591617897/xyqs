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
        if (Integer.valueOf(2).equals(exist.getStatus())) {
            throw BizException.of("已下线的公告不能修改");
        }
        AdminNotice patch = new AdminNotice();
        patch.setId(id);
        patch.setTitle(req.getTitle());
        patch.setContent(req.getContent());
        patch.setType(req.getType());
        patch.setTargetType(req.getTargetType());
        patch.setSchoolId(req.getSchoolId());
        patch.setLevel(req.getLevel());
        patch.setPublishAt(req.getPublishAt());
        noticeMapper.updateById(patch);
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
        noticeMapper.update(null, Wrappers.<AdminNotice>lambdaUpdate()
                .eq(AdminNotice::getId, id)
                .set(AdminNotice::getStatus, 1)
                .set(AdminNotice::getPublishAt, exist.getPublishAt() == null ? LocalDateTime.now() : exist.getPublishAt())
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
}

package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.client.AdminWriteGateway;
import com.campus.admin.config.AdminContext;
import com.campus.admin.config.DataScope;
import com.campus.admin.config.DataScopeResolver;
import com.campus.admin.dto.UserRow;
import com.campus.admin.dto.UserStats;
import com.campus.admin.entity.UserBlacklist;
import com.campus.admin.entity.UserMute;
import com.campus.admin.entity.UserVerifyLog;
import com.campus.admin.mapper.UserBlacklistMapper;
import com.campus.admin.mapper.UserMuteMapper;
import com.campus.admin.mapper.UserQueryMapper;
import com.campus.admin.mapper.UserVerifyLogMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理：列表/详情、封禁、禁言、黑名单、实名审核。
 *
 * <p>三个容易搞混的概念，这里刻意分开：
 * <ul>
 *   <li><b>封禁</b>：写 t_user.status=0，账号不可用，落 t_user_blacklist；</li>
 *   <li><b>禁言</b>：账号可用但不能发言，事实源 t_user_mute，
 *       同时把投影列 t_user.mute_until 刷成一致（C 端只读这一列）；</li>
 *   <li><b>实名审核</b>：只改 t_user.verify_status，不影响账号可用性。</li>
 * </ul>
 * 三者都通过 {@link AdminWriteGateway} 打到 campus-user，本库只留治理流水。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageService {

    /** 永久禁言写成 100 年后：投影列用 NULL 表示「未禁言」，永久不能也用 NULL */
    private static final int PERMANENT_YEARS = 100;

    private final UserQueryMapper userQueryMapper;
    private final UserMuteMapper userMuteMapper;
    private final UserBlacklistMapper userBlacklistMapper;
    private final UserVerifyLogMapper userVerifyLogMapper;
    private final AdminWriteGateway gateway;
    private final DataScopeResolver dataScopeResolver;

    // ---------------- 查询 ----------------

    public R.Page<UserRow> pageUsers(String keyword, Long schoolId, Integer verifyStatus, Integer status,
                                     Integer muted, Integer blacklisted, long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = ds.isAll() ? null : ds.getSchoolIds();
        if (schoolIds != null && schoolIds.isEmpty()) {
            return R.Page.of(List.of(), 0, page, size);
        }
        IPage<UserRow> p = userQueryMapper.selectUsers(new Page<>(page, size), keyword, schoolId,
                verifyStatus, status, muted, blacklisted, schoolIds);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    public UserRow userDetail(Long id) {
        UserRow row = userQueryMapper.selectUser(id);
        if (row == null) {
            throw BizException.notFound("用户不存在");
        }
        assertInScope(row.getSchoolId());
        return row;
    }

    public UserStats userStats(Long id) {
        UserStats stats = new UserStats();
        stats.setPosts(userQueryMapper.countPosts(id));
        stats.setGoods(userQueryMapper.countGoods(id));
        stats.setWall(userQueryMapper.countWall(id));
        stats.setOrders(userQueryMapper.countOrders(id));
        return stats;
    }

    // ---------------- 封禁 / 黑名单 ----------------

    /**
     * 拉黑并封禁：写 t_user.status=0，同时落一条黑名单记录。
     * 两者在同一次调用里完成——只落黑名单不改 status 会出现「在黑名单里但还能发帖」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ban(Long userId, String reasonType, String reason) {
        UserRow row = userDetail(userId);
        UserBlacklist exist = userBlacklistMapper.selectOne(Wrappers.<UserBlacklist>lambdaQuery()
                .eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getStatus, 1));
        if (exist != null) {
            throw BizException.of("该用户已在黑名单中");
        }
        UserBlacklist bl = new UserBlacklist();
        bl.setUserId(userId);
        bl.setUserName(row.getNickname());
        bl.setReasonType(reasonType);
        bl.setReason(reason);
        bl.setStatus(1);
        bl.setOperatorId(AdminContext.adminId());
        bl.setOperatorName(AdminContext.username());
        userBlacklistMapper.insert(bl);
        gateway.setUserStatus(userId, 0, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unban(Long userId) {
        UserBlacklist exist = userBlacklistMapper.selectOne(Wrappers.<UserBlacklist>lambdaQuery()
                .eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getStatus, 1));
        if (exist == null) {
            throw BizException.of("该用户不在黑名单中");
        }
        // 解除是把 status 置 NULL（不是 0）——updateById 会跳过 null 字段，
        // 必须走 lambdaUpdate 的显式 set，否则这一行根本不会被更新。
        userBlacklistMapper.update(null, Wrappers.<UserBlacklist>lambdaUpdate()
                .eq(UserBlacklist::getId, exist.getId())
                .set(UserBlacklist::getStatus, null)
                .set(UserBlacklist::getReleasedAt, LocalDateTime.now()));
        gateway.setUserStatus(userId, 1, "解除拉黑");
    }

    public R.Page<UserBlacklist> pageBlacklist(String keyword, long page, long size) {
        IPage<UserBlacklist> p = userBlacklistMapper.selectPage(new Page<>(page, size),
                Wrappers.<UserBlacklist>lambdaQuery()
                        .eq(UserBlacklist::getStatus, 1)
                        .like(keyword != null && !keyword.isBlank(), UserBlacklist::getUserName, keyword)
                        .orderByDesc(UserBlacklist::getCreatedAt));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    // ---------------- 禁言 ----------------

    /**
     * 禁言。同一 scope 先解除旧的生效记录再插新的，
     * 保证 uk_user_scope_active 不会被撞（也保证不会出现两条同时生效的禁言）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void mute(Long userId, Integer scope, Integer minutes, String reasonType, String reason) {
        userDetail(userId);
        int scopeVal = scope == null ? 1 : scope;
        int mins = minutes == null ? 0 : minutes;
        if (mins < 0) {
            throw BizException.of("禁言时长不能为负");
        }
        releaseActiveMute(userId, scopeVal, 1);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = mins == 0 ? now.plusYears(PERMANENT_YEARS) : now.plusMinutes(mins);

        UserMute mute = new UserMute();
        mute.setUserId(userId);
        mute.setScope(scopeVal);
        mute.setMinutes(mins);
        mute.setReasonType(reasonType);
        mute.setReason(reason);
        mute.setStartAt(now);
        mute.setEndAt(endAt);
        mute.setStatus(1);
        mute.setOperatorId(AdminContext.adminId());
        mute.setOperatorName(AdminContext.username());
        userMuteMapper.insert(mute);

        gateway.setUserMuteUntil(userId, recomputeMuteUntil(userId), reason);
    }

    /** 解除禁言：置 status=NULL 后按剩余生效记录重算投影列 */
    @Transactional(rollbackFor = Exception.class)
    public void unmute(Long userId, Integer scope) {
        int scopeVal = scope == null ? 1 : scope;
        int rows = releaseActiveMute(userId, scopeVal, 1);
        if (rows == 0) {
            throw BizException.of("该用户当前没有被禁言");
        }
        gateway.setUserMuteUntil(userId, recomputeMuteUntil(userId), "人工解除禁言");
    }

    public R.Page<UserMute> pageMutes(Long userId, long page, long size) {
        IPage<UserMute> p = userMuteMapper.selectPage(new Page<>(page, size),
                Wrappers.<UserMute>lambdaQuery()
                        .eq(userId != null, UserMute::getUserId, userId)
                        .orderByDesc(UserMute::getCreatedAt));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    /**
     * 到期自动解封：把 end_at 已过的生效记录置为已解除并刷新投影。
     * 由定时任务调用（P2 接入 @Scheduled），也可由后台手动触发。
     *
     * @return 解除的条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int releaseExpiredMutes() {
        List<UserMute> expired = userMuteMapper.selectList(Wrappers.<UserMute>lambdaQuery()
                .eq(UserMute::getStatus, 1)
                .lt(UserMute::getEndAt, LocalDateTime.now()));
        if (expired.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (UserMute m : expired) {
            releaseMuteRow(m.getId(), now, 2);
            gateway.setUserMuteUntil(m.getUserId(), recomputeMuteUntil(m.getUserId()), "禁言到期自动解除");
        }
        return expired.size();
    }

    private int releaseActiveMute(Long userId, int scope, int releaseType) {
        List<UserMute> active = userMuteMapper.selectList(Wrappers.<UserMute>lambdaQuery()
                .eq(UserMute::getUserId, userId)
                .eq(UserMute::getScope, scope)
                .eq(UserMute::getStatus, 1));
        LocalDateTime now = LocalDateTime.now();
        for (UserMute m : active) {
            releaseMuteRow(m.getId(), now, releaseType);
        }
        return active.size();
    }

    /**
     * 把一条禁言置为已解除（status = NULL）。
     * 走 lambdaUpdate 显式 set null：updateById 会跳过 null 字段，写不进去。
     */
    private void releaseMuteRow(Long muteId, LocalDateTime now, int releaseType) {
        userMuteMapper.update(null, Wrappers.<UserMute>lambdaUpdate()
                .eq(UserMute::getId, muteId)
                .set(UserMute::getStatus, null)
                .set(UserMute::getReleasedAt, now)
                .set(UserMute::getReleaseType, releaseType));
    }

    /**
     * 重算投影列 t_user.mute_until：取该用户所有生效禁言中最晚的到期时间。
     * 一次禁多个 scope 时取最大，解除其中一个不影响其余。
     */
    private LocalDateTime recomputeMuteUntil(Long userId) {
        List<UserMute> active = userMuteMapper.selectList(Wrappers.<UserMute>lambdaQuery()
                .eq(UserMute::getUserId, userId)
                .eq(UserMute::getStatus, 1));
        LocalDateTime max = null;
        for (UserMute m : active) {
            // 防御：老数据的永久禁言可能是 NULL，按「永久」处理
            if (m.getEndAt() == null) {
                return LocalDateTime.now().plusYears(PERMANENT_YEARS);
            }
            if (max == null || m.getEndAt().isAfter(max)) {
                max = m.getEndAt();
            }
        }
        return max;
    }

    // ---------------- 实名审核 ----------------

    /** 待审核列表：verify_status=1（审核中） */
    public R.Page<UserRow> pageVerifyPending(long page, long size) {
        return pageUsers(null, null, 1, null, null, null, page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void verify(Long userId, boolean pass, String reason) {
        UserRow row = userDetail(userId);
        if (!Integer.valueOf(1).equals(row.getVerifyStatus())) {
            throw BizException.of("该用户当前不是「审核中」状态，无法重复处理");
        }
        if (!pass && (reason == null || reason.isBlank())) {
            throw BizException.of("驳回必须填写原因");
        }
        int toStatus = pass ? 2 : 3;
        gateway.setUserVerify(userId, toStatus, pass ? null : reason);

        UserVerifyLog log = new UserVerifyLog();
        log.setUserId(userId);
        log.setUserName(row.getNickname());
        log.setSchoolId(row.getSchoolId());
        log.setSchoolName(row.getSchoolName());
        log.setRealName(row.getRealName());
        log.setStudentNo(row.getStudentNo());
        log.setVerifyImg(row.getVerifyImg());
        log.setFromStatus(row.getVerifyStatus());
        log.setToStatus(toStatus);
        log.setReason(pass ? null : reason);
        log.setAdminId(AdminContext.adminId());
        log.setAdminName(AdminContext.username());
        userVerifyLogMapper.insert(log);
    }

    public R.Page<UserVerifyLog> pageVerifyLogs(Long userId, Integer toStatus, long page, long size) {
        IPage<UserVerifyLog> p = userVerifyLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<UserVerifyLog>lambdaQuery()
                        .eq(userId != null, UserVerifyLog::getUserId, userId)
                        .eq(toStatus != null, UserVerifyLog::getToStatus, toStatus)
                        .orderByDesc(UserVerifyLog::getCreatedAt));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    // ---------------- 内部 ----------------

    private void assertInScope(Long schoolId) {
        DataScope ds = dataScopeResolver.resolve();
        if (ds.isAll()) {
            return;
        }
        if (schoolId == null || !ds.getSchoolIds().contains(schoolId)) {
            throw BizException.forbidden("该用户不在你的管辖范围内");
        }
    }
}

package com.campus.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 注销账号的「保留期满清理」。
 *
 * <p>注销不是立即删除：账号先进入注销中状态（{@code cancelled_at} 非空），
 * 保留期内重新登录可恢复。满保留期后才由这里清除个人信息。
 *
 * <p><b>为什么清理而不是物理删除整行</b>：帖子、闲置、树洞的作者外键都指向
 * {@code t_user.id}，物理删除会让这些内容变成悬空引用（点进作者主页 404、
 * 列表里作者名空白）。清空个人信息 + 昵称落为「已注销用户」，在合规上等同于
 * 删除个人信息，内容仍能正常展示。
 *
 * <p><b>openid 必须换占位值</b>：如果保留原 openid，同一微信号再次登录会命中
 * 这条已清空的记录，用户拿到的是一具空壳账号而不是全新账号；换成
 * {@code purged:<id>} 后下次登录即正常新建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPurgeService {

    /** 注销后对外展示的昵称：内容区作者名、主页标题都用它 */
    public static final String CANCELLED_NICKNAME = "已注销用户";

    /** openid 占位前缀。冒号不在微信 openid 字符集里，不会和真实 openid 撞车 */
    private static final String PURGED_OPENID_PREFIX = "purged:";

    private final UserMapper userMapper;

    /**
     * 注销后个人信息保留天数。期间重新登录可恢复，满期清除。
     * 需求写死「1 年」，但做成配置项：法务口径调整时不用改代码。
     */
    @Value("${campus.account.cancel-retain-days:365}")
    private int retainDays;

    public int retainDays() {
        return retainDays;
    }

    /** 是否已过保留期（该清个人信息了） */
    public boolean isDue(User user) {
        return user != null
                && user.getCancelledAt() != null
                && user.getPurgedAt() == null
                && user.getCancelledAt().plusDays(retainDays).isBefore(LocalDateTime.now());
    }

    /**
     * 批量清理到期账号，返回处理条数。
     *
     * <p>分批（limit）而不是一次全表扫：清理是定时任务，单次时间可控，
     * 即使积压几十万条也不会长时间持锁。
     */
    @Transactional(rollbackFor = Exception.class)
    public int purgeExpired(int limit) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(retainDays);
        List<User> list = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .select(User::getId)
                .isNotNull(User::getCancelledAt)
                .isNull(User::getPurgedAt)
                .lt(User::getCancelledAt, deadline)
                .orderByAsc(User::getId)
                .last("LIMIT " + Math.max(1, limit)));
        int count = 0;
        for (User u : list) {
            if (purge(u.getId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 清除单个账号的个人信息。
     *
     * <p>用 Wrapper 显式 SET 而不是 updateById：MyBatis-Plus 默认 NOT_NULL 字段策略
     * 会跳过 null 字段，那样头像、手机号永远清不掉（库里还是旧值）。
     *
     * <p>幂等：{@code purged_at IS NULL} 作为前置条件，重复调用不会二次改写。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean purge(Long userId) {
        if (userId == null) {
            return false;
        }
        int rows = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .isNull(User::getPurgedAt)
                .set(User::getNickname, CANCELLED_NICKNAME)
                .set(User::getAvatar, null)
                .set(User::getBio, null)
                .set(User::getPhone, null)
                .set(User::getPhoneHash, null)
                .set(User::getRealName, null)
                .set(User::getStudentNo, null)
                .set(User::getStudentNoHash, null)
                .set(User::getSchoolId, null)
                .set(User::getVerifyStatus, 0)
                .set(User::getVerifySource, 0)
                .set(User::getVerifyImg, null)
                .set(User::getVerifyRemark, null)
                .set(User::getVerifySubmitAt, null)
                .set(User::getWxStudentVerified, 0)
                .set(User::getWxStudentStatus, null)
                .set(User::getWxStudentVerifyAt, null)
                .set(User::getCancelNicknameBak, null)
                .set(User::getCancelAvatarBak, null)
                .set(User::getCancelBioBak, null)
                // openid 换占位：保留原值会让同一微信号登录时命中这条空壳记录
                .set(User::getOpenid, PURGED_OPENID_PREFIX + userId)
                .set(User::getUnionid, null)
                .set(User::getPurgedAt, LocalDateTime.now()));
        if (rows > 0) {
            log.info("[account] 注销账号 {} 已满保留期，个人信息已清除", userId);
        }
        return rows > 0;
    }
}

package com.campus.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.api.R;
import com.campus.common.client.SecurityGuard;
import com.campus.common.client.dto.StudentCheckReq;
import com.campus.common.client.dto.StudentCheckVO;
import com.campus.common.crypto.SearchHash;
import com.campus.common.exception.BizException;
import com.campus.common.security.JwtService;
import com.campus.user.client.GoodsCountClient;
import com.campus.user.client.GroupJoinClient;
import com.campus.user.client.PostCountClient;
import com.campus.user.client.StudentVerifyClient;
import com.campus.user.client.WallCountClient;
import com.campus.user.dto.LoginOrCreateReq;
import com.campus.user.dto.ProfileVO;
import com.campus.user.dto.RestoreResp;
import com.campus.user.dto.SchoolRegion;
import com.campus.user.dto.UpdateProfileReq;
import com.campus.user.dto.UserBrief;
import com.campus.user.dto.UserVO;
import com.campus.user.dto.VerifyReq;
import com.campus.user.dto.VerifyResp;
import com.campus.user.dto.WxVerifyReq;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final FollowService followService;
    private final GoodsCountClient goodsCountClient;
    private final PostCountClient postCountClient;
    private final WallCountClient wallCountClient;
    private final SecurityGuard securityGuard;
    private final StudentVerifyClient studentVerifyClient;
    private final GroupJoinClient groupJoinClient;
    private final AccountPurgeService accountPurgeService;

    @Value("${campus.verify.auto-approve:true}")
    private boolean autoApprove;

    /**
     * 重认证冷却天数：已认证用户换学校 / 换材料的两次提交最短间隔。
     * 认证直接决定「能发到哪个学校分区」，不设冷却就成了免费的换校开关。
     */
    @Value("${campus.verify.reapply-cooldown-days:7}")
    private int reapplyCooldownDays;

    /**
     * 登录时按 openid 获取或创建用户（供 auth 服务通过 Feign 调用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserBrief loginOrCreate(LoginOrCreateReq req) {
        if (!StringUtils.hasText(req.getOpenid())) {
            throw new BizException("openid 不能为空");
        }
        // openid 只在宿主平台内唯一，必须带 platform 一起定位
        String platform = StringUtils.hasText(req.getPlatform()) ? req.getPlatform() : "wx";
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getPlatform, platform)
                .eq(User::getOpenid, req.getOpenid()));
        // 注销已满保留期：先清除个人信息（openid 会被换成占位值），再按新用户处理。
        // 用户此时重新登录拿到的是一个全新账号，不会继承任何旧数据。
        if (accountPurgeService.isDue(user)) {
            accountPurgeService.purge(user.getId());
            user = null;
        }
        if (user == null) {
            user = new User();
            user.setPlatform(platform);
            user.setOpenid(req.getOpenid());
            user.setUnionid(req.getUnionid());
            user.setNickname(StringUtils.hasText(req.getNickname())
                    ? req.getNickname() : "校园用户" + ThreadLocalRandom.current().nextInt(1000, 9999));
            user.setAvatar(req.getAvatar());
            user.setGender(0);
            user.setVerifyStatus(0);
            user.setStatus(1);
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                // uk_platform_openid 并发兜底：小程序冷启动时首页与「我的」页可能同时
                // ensureLogin，两个请求都读到 user==null 各插一条，后到者撞唯一键。
                // 关系行已由并发事务建立，重新读回即可，不能让用户看到 500。
                User exist = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                        .eq(User::getPlatform, platform)
                        .eq(User::getOpenid, req.getOpenid()));
                if (exist == null) {
                    throw e;
                }
                return toBrief(exist);
            }
        } else {
            // 昵称/头像以小程序授权为准（仅补齐空值，避免覆盖用户自定义资料）
            boolean needUpdate = false;
            if (!StringUtils.hasText(user.getAvatar()) && StringUtils.hasText(req.getAvatar())) {
                user.setAvatar(req.getAvatar());
                needUpdate = true;
            }
            if (!StringUtils.hasText(user.getNickname()) && StringUtils.hasText(req.getNickname())) {
                user.setNickname(req.getNickname());
                needUpdate = true;
            }
            if (needUpdate) {
                userMapper.updateById(user);
            }
        }
        return toBrief(user);
    }

    public UserVO me(Long userId) {
        User user = requireUser(userId);
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setPhone(user.getPhone());
        vo.setBio(user.getBio());
        vo.setSchoolId(user.getSchoolId());
        vo.setSchoolName(user.getSchoolId() == null ? null : userMapper.selectSchoolName(user.getSchoolId()));
        if (user.getSchoolId() != null) {
            SchoolRegion region = userMapper.selectSchoolRegion(user.getSchoolId());
            if (region != null) {
                vo.setSchoolProvince(region.getProvince());
                vo.setSchoolCity(region.getCity());
                vo.setSchoolDistrict(region.getDistrict());
            }
        }
        vo.setStudentNo(maskStudentNo(user.getStudentNo()));
        vo.setVerifyStatus(user.getVerifyStatus());
        vo.setVerifySource(user.getVerifySource());
        vo.setWxStudentVerified(user.getWxStudentVerified());
        vo.setVerifyRemark(user.getVerifyRemark());
        vo.setVerifyImg(user.getVerifyImg());
        vo.setVerifySubmitAt(user.getVerifySubmitAt());
        // reapplyAt 只在「已认证且还在冷却期」时有值；null 表示可以立即重新认证。
        // 冷却天数由后端算完再下发，前端别自己拍天数。
        if (user.getVerifyStatus() != null && user.getVerifyStatus() == 2 && user.getVerifySubmitAt() != null) {
            LocalDateTime allowAt = user.getVerifySubmitAt().plusDays(reapplyCooldownDays);
            vo.setReapplyAt(LocalDateTime.now().isBefore(allowAt) ? allowAt : null);
        }
        vo.setFansCount(nz(user.getFansCount()));
        vo.setFollowCount(nz(user.getFollowCount()));
        vo.setCreatedAt(user.getCreatedAt());
        // 「我的」页统计：跨服务 Feign 聚合，失败各自降级 0，不打崩个人中心
        vo.setGoodsCount(goodsCount(userId, null));
        vo.setActiveCount(goodsCount(userId, 1));
        vo.setSoldCount(goodsCount(userId, 2));
        int postCnt = postCount(userId);
        vo.setPostCount(postCnt);
        // 我的动态 = 帖子 + 表白墙全部类别，和「我的动态」页的「全部动态」对齐，
        // 否则一边 1 一边 2（只算帖子会漏掉表白/树洞）
        vo.setDynamicCount(postCnt + wallCount(userId, null));
        vo.setFavoriteCount(favoriteCount(userId));
        vo.setBuyOrderCount(orderCount(userId, "buyer", null));
        vo.setSellOrderCount(orderCount(userId, "seller", null));
        vo.setIntentCount(orderCount(userId, "buyer", 0));
        vo.setConfessionCount(wallCount(userId, "love"));
        vo.setTreeholeCount(wallCount(userId, "tree"));
        vo.setCreditScore(creditScore(user, vo));
        return vo;
    }

    /* ==================== 账号注销 / 恢复 ==================== */

    /**
     * 注销账号（保留期内可恢复）。
     *
     * <p>一次事务里做三件事：
     * <ol>
     *   <li>备份昵称 / 头像 / 简介 —— 保留期内恢复要原样还原；</li>
     *   <li>昵称落为「已注销用户」，头像、简介、手机号、校园认证全部清空
     *       —— 内容区作者名立刻变成已注销，也不再通过头像把人对上号；</li>
     *   <li>写 {@code cancelled_at} 进入保留期，满期由 {@link AccountPurgeService} 清理。</li>
     * </ol>
     *
     * <p>学号（含盲索引）刻意留到保留期结束：学号属于「基础信息保留」的范围，
     * 而且立刻释放会让人用「注销 → 重新注册」绕开同校学号唯一，白送换号能力。
     *
     * <p>封禁中的账号同样允许注销：数据删除是用户权利，封禁不该成为阻碍。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelAccount(Long userId) {
        User user = requireUser(userId);
        if (user.getCancelledAt() != null) {
            throw new BizException("账号已在注销流程中，重新登录可恢复");
        }
        // 昵称可能本来就是空的（用户从没设置过），备份时留 null 即可
        String nicknameBak = StringUtils.hasText(user.getNickname()) ? user.getNickname() : null;
        int rows = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .isNull(User::getCancelledAt)
                .set(User::getCancelledAt, LocalDateTime.now())
                .set(User::getCancelNicknameBak, nicknameBak)
                .set(User::getCancelAvatarBak, user.getAvatar())
                .set(User::getCancelBioBak, user.getBio())
                .set(User::getNickname, AccountPurgeService.CANCELLED_NICKNAME)
                .set(User::getAvatar, null)
                .set(User::getBio, null)
                // 手机号是个人信息，注销即解绑；恢复后需要用户重新绑定
                .set(User::getPhone, null)
                .set(User::getPhoneHash, null)
                // 校园认证同步失效：恢复后必须重新认证。
                // 若保留认证，用户就能靠「注销→恢复」重置重认证冷却，把认证当换校开关。
                .set(User::getVerifyStatus, 0)
                .set(User::getVerifySource, 0)
                .set(User::getVerifyImg, null)
                .set(User::getVerifyRemark, null)
                .set(User::getVerifySubmitAt, null)
                .set(User::getWxStudentVerified, 0)
                .set(User::getWxStudentStatus, null)
                // 令牌版本 +1：注销必须立刻让旧令牌作废。
                // 否则「注销 → 继续用旧令牌发内容」这条路是通的（内容服务只看令牌不看 status）。
                .setSql("token_version = token_version + 1"));
        if (rows == 0) {
            // 并发重复提交：另一笔已经注销成功，按同一结果处理，不报错
            throw new BizException("账号已在注销流程中，重新登录可恢复");
        }
        log.info("[account] 用户 {} 已注销，进入保留期（{} 天）", userId, accountPurgeService.retainDays());
    }

    /**
     * 恢复注销账号（保留期内）。过期账号会先被清理，再拒绝恢复。
     *
     * <p>这里不能用 {@link #requireUser}：它专门拦注销中的账号，而本方法处理的
     * 恰恰就是这些账号。身份已经由 JWT 校验过，直接按 id 取就行。
     */
    @Transactional(rollbackFor = Exception.class)
    public RestoreResp restoreAccount(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        // 幂等：没注销过就把当前状态原样还回去，前端按正常登录继续，不报错
        if (user.getCancelledAt() == null) {
            return new RestoreResp(
                    jwtService.create(user.getId(), user.getSchoolId(), user.getVerifyStatus(), user.getTokenVersion()), toBrief(user));
        }
        if (accountPurgeService.isDue(user)) {
            /*
             * 已过保留期但定时任务还没扫到：直接拒绝，不要在这里顺手清理。
             * 本方法在事务里，清理后紧接着抛 403 会把清理一起回滚，白跑一趟。
             * 真正的清理交给定时任务（以及下次登录时的 loginOrCreate）。
             */
            throw new BizException("账号注销已满 " + accountPurgeService.retainDays()
                    + " 天，资料不再保留，请重新登录使用新账号");
        }
        LambdaUpdateWrapper<User> update = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getCancelledAt, null)
                .set(User::getCancelNicknameBak, null)
                .set(User::getCancelAvatarBak, null)
                .set(User::getCancelBioBak, null)
                // 令牌版本再 +1：恢复动作同样作废旧令牌。
                // 恢复后签的新令牌用的是 +1 之后的值，所以本次登录不受影响。
                .setSql("token_version = token_version + 1");
        if (StringUtils.hasText(user.getCancelNicknameBak())) {
            update.set(User::getNickname, user.getCancelNicknameBak());
        } else {
            // 注销前就没有昵称：给个默认名，否则恢复后到处显示空白
            update.set(User::getNickname, "校园用户" + ThreadLocalRandom.current().nextInt(1000, 9999));
        }
        if (StringUtils.hasText(user.getCancelAvatarBak())) {
            update.set(User::getAvatar, user.getCancelAvatarBak());
        }
        if (StringUtils.hasText(user.getCancelBioBak())) {
            update.set(User::getBio, user.getCancelBioBak());
        }
        userMapper.update(null, update);
        User fresh = userMapper.selectById(userId);
        log.info("[account] 用户 {} 已恢复注销账号", userId);
        return new RestoreResp(
                jwtService.create(fresh.getId(), fresh.getSchoolId(), fresh.getVerifyStatus(), fresh.getTokenVersion()), toBrief(fresh));
    }

    /**
     * 公开主页资料与统计（未登录可访问，viewerId 为空）。
     * 被封禁 / 不存在统一 404；敏感字段（手机号/学号/真实姓名/认证照片）不返回；
     * 跨服务计数（闲置/帖子）走 Feign 聚合，失败降级 0 并打 warn，不打崩主页。
     */
    public ProfileVO profile(Long userId, Long viewerId) {
        User user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw BizException.notFound("用户不存在或已被封禁");
        }
        ProfileVO vo = new ProfileVO();
        // 注销中的账号：昵称已落库为「已注销用户」，学校与认证也不再展示
        // —— 保留学校会让「已注销用户 · 贵州大学」这种组合把人对上号
        boolean cancelled = user.getCancelledAt() != null;
        vo.setId(user.getId());
        vo.setNickname(cancelled ? AccountPurgeService.CANCELLED_NICKNAME : user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(cancelled ? null : user.getBio());
        vo.setSchoolId(cancelled ? null : user.getSchoolId());
        vo.setSchoolName(cancelled || user.getSchoolId() == null
                ? null : userMapper.selectSchoolName(user.getSchoolId()));
        vo.setVerifyStatus(cancelled ? 0 : user.getVerifyStatus());
        vo.setFansCount(nz(user.getFansCount()));
        vo.setFollowCount(nz(user.getFollowCount()));
        boolean isSelf = viewerId != null && viewerId.equals(userId);
        vo.setIsSelf(isSelf);
        // 未登录 / 本人 → false
        boolean following = !isSelf && followService.status(viewerId, userId);
        vo.setFollowing(following);
        // 对方是否关注我（互关判断的另一半）：只查一次 countPair，走 uk_pair 索引
        vo.setFollowedBy(!isSelf && viewerId != null && followService.status(userId, viewerId));
        // 跨服务计数聚合：Feign 失败降级 0 + warn 日志，主页整体不 500
        vo.setGoodsSaleCount(goodsCount(userId, 1));
        vo.setGoodsTotalCount(goodsCount(userId, null));
        vo.setPostCount(postCount(userId));
        return vo;
    }

    public UserVO updateProfile(Long userId, UpdateProfileReq req) {
        User user = requireUser(userId);
        if (StringUtils.hasText(req.getNickname())) {
            // 先本地净化（level 2 替换生效），再送微信审核；命中 level 3 时 purify 直接抛 400
            String nickname = securityGuard.purify(userId, req.getNickname().trim(),
                    SecurityGuard.BIZ_NICKNAME);
            securityGuard.checkText(userId, nickname, SecurityGuard.SCENE_PROFILE,
                    SecurityGuard.BIZ_NICKNAME);
            user.setNickname(nickname);
        }
        if (StringUtils.hasText(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender());
        }
        if (req.getPhone() != null) {
            String phone = req.getPhone().trim();
            user.setPhone(phone);
            // 手机号列已加密，等值搜索只能靠盲索引；这里同步维护，避免后台搜不到人
            user.setPhoneHash(phone.isEmpty() ? null : SearchHash.of("phone", phone));
        }
        if (req.getBio() != null) {
            user.setBio(req.getBio());
        }
        // 新用户引导「选学校」：只允许未完成学籍认证的用户走这条轻量通道。
        // 已认证用户换校必须走 /user/verify 重认证（带材料 + 人工复核），否则等于绕过认证。
        if (req.getSchoolId() != null) {
            if (user.getVerifyStatus() != null && user.getVerifyStatus() == 2) {
                throw new BizException("你已完成学籍认证，如需更换学校请到「学籍认证」页重新认证");
            }
            if (userMapper.countSchool(req.getSchoolId()) == 0) {
                throw new BizException("所选学校不存在，请重新选择");
            }
            user.setSchoolId(req.getSchoolId());
        }
        userMapper.updateById(user);
        // 绑定学校的三个入口之一：选校（主入口）
        afterSchoolBound(user.getId(), user.getSchoolId());
        return me(userId);
    }

    /**
     * 提交校园身份认证。认证通过后返回带新 verifyStatus 的 token。
     *
     * <p>已认证（verifyStatus=2）用户不是被拒绝，而是转入「重认证」：
     * 冷却期外可重新提交，但结果一律置为 1（审核中）强制人工复核——
     * auto-approve 只适用于首次提交，否则一次调用就能把学校改到任意学校。
     */
    @Transactional(rollbackFor = Exception.class)
    public VerifyResp submitVerify(Long userId, VerifyReq req) {
        User user = requireUser(userId);
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被封禁，无法认证");
        }
        boolean reapply = user.getVerifyStatus() != null && user.getVerifyStatus() == 2;
        if (reapply) {
            ensureReapplyAllowed(user);
        }
        if (userMapper.countSchool(req.getSchoolId()) == 0) {
            throw new BizException("所选学校不存在，请重新选择");
        }
        if (userMapper.countStudentNoUsed(req.getSchoolId(),
                SearchHash.of(req.getSchoolId() == null ? null : String.valueOf(req.getSchoolId()), req.getStudentNo()),
                userId) > 0) {
            throw new BizException("该学号已被其他账号认证，请联系管理员");
        }

        // 改完 school_id 就查不回原学校了，后台审核时需要知道「谁从哪所学校换到哪所」
        String oldSchoolName = reapply && user.getSchoolId() != null
                ? userMapper.selectSchoolName(user.getSchoolId()) : null;

        user.setSchoolId(req.getSchoolId());
        user.setStudentNo(req.getStudentNo().trim());
        // 盲索引与密文同事务落库：查重、后续换校复核都靠它
        user.setStudentNoHash(SearchHash.of(String.valueOf(req.getSchoolId()), req.getStudentNo()));
        user.setRealName(req.getRealName().trim());
        user.setVerifyImg(req.getVerifyImg());
        user.setVerifySource(1);
        user.setVerifySubmitAt(LocalDateTime.now());
        if (reapply) {
            user.setVerifyStatus(1);
            // remark 在 status=1 时不展示给用户（前端只在驳回=3 时读它），这里当审核备注用
            user.setVerifyRemark("重认证申请：原学校 " + (oldSchoolName == null ? "未记录" : oldSchoolName));
        } else {
            user.setVerifyStatus(autoApprove ? 2 : 1);
            user.setVerifyRemark("");
        }
        userMapper.updateById(user);
        // 绑定学校的三个入口之二：学生证认证
        afterSchoolBound(user.getId(), user.getSchoolId());

        String token = jwtService.create(user.getId(), user.getSchoolId(), user.getVerifyStatus(), user.getTokenVersion());
        String msg = user.getVerifyStatus() == 2
                ? "认证成功，欢迎来到校聚一城"
                : (reapply ? "重认证申请已提交，等待人工复核" : "已提交认证，预计 1 个工作日内完成审核");
        return new VerifyResp(user.getVerifyStatus(), token, msg);
    }

    /**
     * 微信学生身份认证（免材料通道）。
     *
     * <p>与 {@link #submitVerify} 的区别：
     * <ul>
     *   <li>不要求学号 / 姓名 / 学生证照片——微信侧已经核过学籍，再要材料是重复收集个人信息；</li>
     *   <li>不落 code（一次性凭据，且含学籍查询能力）；</li>
     *   <li>通过即认证成功（verifyStatus=2），不走 auto-approve 那个开关——
     *       人工开关是给「自己看照片」用的，微信结论不需要二次审核。</li>
     * </ul>
     *
     * <p>仍然要求学校：微信不返回学校，而本站的内容分区（同校/同城）依赖 school_id，
     * 不选学校就会让「本校」筛选退化成全站。
     */
    @Transactional(rollbackFor = Exception.class)
    public VerifyResp submitWxStudentVerify(Long userId, WxVerifyReq req) {
        User user = requireUser(userId);
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被封禁，无法认证");
        }
        boolean reapply = user.getVerifyStatus() != null && user.getVerifyStatus() == 2;
        if (reapply) {
            // 冷却校验放在调微信之前：code 是一次性凭据，先拦下来别浪费
            ensureReapplyAllowed(user);
        }
        if (req.getSchoolId() == null || userMapper.countSchool(req.getSchoolId()) == 0) {
            throw new BizException("所选学校不存在，请重新选择");
        }

        R<StudentCheckVO> res;
        try {
            res = studentVerifyClient.check(new StudentCheckReq(userId, req.getCode()));
        } catch (Exception e) {
            // 网络 / 服务未起 / 超时：不能把「调用挂了」说成「你不是学生」
            log.warn("微信学生认证调用失败 userId={}: {}: {}", userId, e.getClass().getSimpleName(), e.getMessage());
            throw new BizException("微信学生认证服务暂时不可用，请稍后再试或改用学生证认证");
        }
        if (res == null) {
            throw new BizException("微信学生认证服务暂时不可用，请稍后再试或改用学生证认证");
        }
        if (res.getCode() != 0) {
            // auth 侧已经把微信错误码翻译成用户话术，这里原样透传
            throw new BizException(res.getMsg() == null ? "微信学生认证未通过" : res.getMsg());
        }
        StudentCheckVO check = res.getData();
        if (check == null) {
            throw new BizException("微信学生认证未返回结果，请稍后再试");
        }
        if (!check.passed()) {
            if (check.bindStatus() != null && check.bindStatus() == 2) {
                throw new BizException("微信正在核验你的学籍，请稍后再查看");
            }
            throw new BizException("微信未确认你的学生身份，可改用学生证认证");
        }

        String oldSchoolName = reapply && user.getSchoolId() != null
                ? userMapper.selectSchoolName(user.getSchoolId()) : null;

        user.setSchoolId(req.getSchoolId());
        user.setVerifySource(2);
        user.setWxStudentVerified(1);
        user.setWxStudentStatus(check.bindStatus());
        user.setWxStudentVerifyAt(LocalDateTime.now());
        user.setVerifySubmitAt(LocalDateTime.now());
        if (reapply) {
            // 微信只核「是否在读」，换校是本站的事：重认证必须人工确认学校，
            // 否则等于把「同校分区」交给用户自选。
            user.setVerifyStatus(1);
            user.setVerifyRemark("重认证申请：原学校 " + (oldSchoolName == null ? "未记录" : oldSchoolName));
        } else {
            user.setVerifyStatus(2);
            user.setVerifyRemark("");
        }
        userMapper.updateById(user);
        // 绑定学校的三个入口之三：微信学生认证
        afterSchoolBound(user.getId(), user.getSchoolId());

        // JWT 里的 schoolId / vs 是登录快照，认证后必须重签，否则前端仍是「未认证」
        String token = jwtService.create(user.getId(), user.getSchoolId(), user.getVerifyStatus(), user.getTokenVersion());
        if (reapply) {
            return new VerifyResp(1, token, "重认证申请已提交，等待人工复核");
        }
        return new VerifyResp(2, token, "微信学生认证通过，欢迎来到校聚一城");
    }

    /**
     * 重认证冷却校验。
     *
     * <p>只约束「已认证」用户：未认证 / 驳回 / 审核中的人重复提交不会带来任何状态收益，
     * 没必要额外限频。冷却天数走配置 {@code campus.verify.reapply-cooldown-days}，
     * 不写死——运营可能要按投诉量调。
     */
    private void ensureReapplyAllowed(User user) {
        LocalDateTime last = user.getVerifySubmitAt();
        if (last == null) {
            // 历史用户（迁移前认证的）没有这个时间戳，放行；
            // 迁移脚本已用 updated_at 回填，正常情况下走不到这里
            return;
        }
        LocalDateTime allowAt = last.plusDays(reapplyCooldownDays);
        if (LocalDateTime.now().isBefore(allowAt)) {
            throw new BizException("距上次提交不足 " + reapplyCooldownDays + " 天，"
                    + allowAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 后可再次申请");
        }
    }

    /**
     * 绑定学校后自动加入校友群（学校没有群则由 message 侧自动建群）。
     *
     * <p><b>必须在 {@code userMapper.updateById(user)} 之后调用</b>：
     * 放前面时事务还没提交，message 服务通过 Feign 查到的是旧数据。
     *
     * <p>fail-soft：入群失败绝不能让「绑定学校」失败（用户会以为选校没成功）。
     * 真正的兜底在进群页的 lazy join —— 学校匹配但无成员记录时会自动补加入。
     */
    private void afterSchoolBound(Long userId, Long schoolId) {
        if (userId == null || schoolId == null) {
            return;
        }
        try {
            R<java.util.Map<String, Object>> r = groupJoinClient.autoJoin(
                    new com.campus.user.dto.AutoJoinReq(userId, schoolId));
            if (r == null || r.getCode() != 0) {
                log.warn("自动入群未成功 userId={} schoolId={} code={} msg={}",
                        userId, schoolId, r == null ? null : r.getCode(), r == null ? null : r.getMsg());
            }
        } catch (Exception e) {
            // 群服务临时不可用 / 未启动：不能影响选校流程
            log.warn("自动入群失败 userId={} schoolId={}: {}: {}",
                    userId, schoolId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    public UserBrief brief(Long userId) {
        return toBrief(requireUser(userId));
    }

    /**
     * 批量取用户摘要，供会话列表一次拉齐对端昵称/头像，避免 N+1 调用。
     */
    public List<UserBrief> briefs(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().limit(200).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream().map(this::toBrief).toList();
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        /*
         * 注销中的账号：本地登录态虽然被清掉了，但旧 token 在有效期内（最长 72h）
         * 仍可能从别的设备打进来，所以真正的业务入口必须在这里兜住。
         *
         * 用 403 而不是 401：401 会被前端 request 层判成「掉线」并清登录态，
         * 而这条链路是「重新登录询问是否恢复」的正常路径，不该表现成故障。
         */
        if (user.getCancelledAt() != null) {
            throw new BizException(403, "账号已注销，重新登录可恢复");
        }
        return user;
    }

    /** 闲置计数聚合（status 可空 = 全部）；Feign 失败降级 0 并打 warn */
    private int goodsCount(Long userId, Integer status) {
        try {
            R<Long> res = goodsCountClient.count(userId, status);
            if (res != null && res.getData() != null && res.getData() > 0) {
                return res.getData().intValue();
            }
        } catch (Exception e) {
            log.warn("聚合闲置计数失败 userId={}, status={}: {}", userId, status, e.getMessage());
        }
        return 0;
    }

    /** 帖子计数聚合；Feign 失败降级 0 并打 warn */
    private int postCount(Long userId) {
        try {
            R<Long> res = postCountClient.count(userId);
            if (res != null && res.getData() != null && res.getData() > 0) {
                return res.getData().intValue();
            }
        } catch (Exception e) {
            log.warn("聚合帖子计数失败 userId={}: {}", userId, e.getMessage());
        }
        return 0;
    }

    /** 商品收藏计数聚合；Feign 失败降级 0 并打 warn */
    private int favoriteCount(Long userId) {
        try {
            R<Long> res = goodsCountClient.favoritesCount(userId);
            if (res != null && res.getData() != null && res.getData() > 0) {
                return res.getData().intValue();
            }
        } catch (Exception e) {
            log.warn("聚合收藏计数失败 userId={}: {}", userId, e.getMessage());
        }
        return 0;
    }

    /** 订单计数聚合（role=buyer/seller，status 可空）；Feign 失败降级 0 并打 warn */
    private int orderCount(Long userId, String role, Integer status) {
        try {
            R<Long> res = goodsCountClient.ordersCount(userId, role, status);
            if (res != null && res.getData() != null && res.getData() > 0) {
                return res.getData().intValue();
            }
        } catch (Exception e) {
            log.warn("聚合订单计数失败 userId={}, role={}: {}", userId, role, e.getMessage());
        }
        return 0;
    }

    /** 表白墙计数聚合（category 可空）；Feign 失败降级 0 并打 warn */
    private int wallCount(Long userId, String category) {
        try {
            R<Long> res = wallCountClient.count(userId, category);
            if (res != null && res.getData() != null && res.getData() > 0) {
                return res.getData().intValue();
            }
        } catch (Exception e) {
            log.warn("聚合表白墙计数失败 userId={}, category={}: {}", userId, category, e.getMessage());
        }
        return 0;
    }

    /**
     * 校园信用分：基础 600 + 已认证 185 + 活跃度加成（发布+卖出+帖子，最多 50）。
     * 折合范围约 600~835，与「极好 / 良好 / 一般」档位配合展示。
     */
    private int creditScore(User user, UserVO vo) {
        boolean verified = user.getVerifyStatus() != null && user.getVerifyStatus() == 2;
        int base = verified ? 785 : 600;
        int activity = Math.min(vo.getGoodsCount() + vo.getSoldCount() + vo.getPostCount(), 50);
        return base + activity;
    }

    /** 计数字段兜底：历史行 / 空值统一为 0 */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 网关用的账号准入查询：只回 {@code status} 与 {@code tokenVersion}。
     *
     * <p>为什么单独一个方法而不是复用 {@link #brief}：网关每个「缓存未命中」的请求都会回源，
     * 这条路径要尽量轻——不查学校名、不聚合跨服务计数、不做任何加解密。
     *
     * @return 用户不存在时返回 {@code null}（网关按「令牌失效」处理）
     */
    public UserBrief authState(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        UserBrief brief = new UserBrief();
        brief.setId(user.getId());
        brief.setStatus(user.getStatus());
        brief.setTokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        return brief;
    }

    private UserBrief toBrief(User user) {
        UserBrief brief = new UserBrief();
        brief.setId(user.getId());
        brief.setNickname(user.getNickname());
        brief.setAvatar(user.getAvatar());
        brief.setSchoolId(user.getSchoolId());
        brief.setVerifyStatus(user.getVerifyStatus());
        // 封禁状态必须下发：认证服务靠它拒绝登录（否则封禁号重开小程序就能换新令牌）
        brief.setStatus(user.getStatus());
        // 令牌版本：签发令牌时写进 claim tv，网关据此判定旧令牌是否已失效
        brief.setTokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        // 登录时要靠它判断「这个号已注销，先问用户要不要恢复」
        brief.setCancelledAt(user.getCancelledAt());
        return brief;
    }

    private String maskStudentNo(String studentNo) {
        if (!StringUtils.hasText(studentNo)) {
            return null;
        }
        if (studentNo.length() <= 4) {
            return "****";
        }
        String tail = studentNo.substring(studentNo.length() - 4);
        return "****" + tail;
    }
}

package com.campus.user.service;

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

    @Value("${campus.verify.auto-approve:true}")
    private boolean autoApprove;

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
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setSchoolId(user.getSchoolId());
        vo.setSchoolName(user.getSchoolId() == null ? null : userMapper.selectSchoolName(user.getSchoolId()));
        vo.setVerifyStatus(user.getVerifyStatus());
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
        // 已认证用户换校必须重新提交 /user/verify（学生证审核），否则等于绕过认证。
        if (req.getSchoolId() != null) {
            if (user.getVerifyStatus() != null && user.getVerifyStatus() == 2) {
                throw new BizException("你已完成学籍认证，如需更换学校请重新提交认证");
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
     */
    @Transactional(rollbackFor = Exception.class)
    public VerifyResp submitVerify(Long userId, VerifyReq req) {
        User user = requireUser(userId);
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被封禁，无法认证");
        }
        if (user.getVerifyStatus() != null && user.getVerifyStatus() == 2) {
            throw new BizException("你已完成身份认证");
        }
        if (userMapper.countSchool(req.getSchoolId()) == 0) {
            throw new BizException("所选学校不存在，请重新选择");
        }
        if (userMapper.countStudentNoUsed(req.getSchoolId(),
                SearchHash.of(req.getSchoolId() == null ? null : String.valueOf(req.getSchoolId()), req.getStudentNo()),
                userId) > 0) {
            throw new BizException("该学号已被其他账号认证，请联系管理员");
        }

        user.setSchoolId(req.getSchoolId());
        user.setStudentNo(req.getStudentNo().trim());
        // 盲索引与密文同事务落库：查重、后续换校复核都靠它
        user.setStudentNoHash(SearchHash.of(String.valueOf(req.getSchoolId()), req.getStudentNo()));
        user.setRealName(req.getRealName().trim());
        user.setVerifyImg(req.getVerifyImg());
        user.setVerifyStatus(autoApprove ? 2 : 1);
        user.setVerifySource(1);
        user.setVerifyRemark("");
        userMapper.updateById(user);
        // 绑定学校的三个入口之二：学生证认证
        afterSchoolBound(user.getId(), user.getSchoolId());

        String token = jwtService.create(user.getId(), user.getSchoolId(), user.getVerifyStatus());
        String msg = autoApprove ? "认证成功，欢迎来到校聚一城" : "已提交认证，预计 1 个工作日内完成审核";
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
        if (user.getVerifyStatus() != null && user.getVerifyStatus() == 2) {
            throw new BizException("你已完成身份认证");
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

        user.setSchoolId(req.getSchoolId());
        user.setVerifyStatus(2);
        user.setVerifySource(2);
        user.setWxStudentVerified(1);
        user.setWxStudentStatus(check.bindStatus());
        user.setWxStudentVerifyAt(LocalDateTime.now());
        user.setVerifyRemark("");
        userMapper.updateById(user);
        // 绑定学校的三个入口之三：微信学生认证
        afterSchoolBound(user.getId(), user.getSchoolId());

        // JWT 里的 schoolId / vs 是登录快照，认证后必须重签，否则前端仍是「未认证」
        String token = jwtService.create(user.getId(), user.getSchoolId(), user.getVerifyStatus());
        return new VerifyResp(2, token, "微信学生认证通过，欢迎来到校聚一城");
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

    private UserBrief toBrief(User user) {
        UserBrief brief = new UserBrief();
        brief.setId(user.getId());
        brief.setNickname(user.getNickname());
        brief.setAvatar(user.getAvatar());
        brief.setSchoolId(user.getSchoolId());
        brief.setVerifyStatus(user.getVerifyStatus());
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

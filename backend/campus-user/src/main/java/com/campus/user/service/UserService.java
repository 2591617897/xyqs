package com.campus.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.exception.BizException;
import com.campus.common.security.JwtService;
import com.campus.user.dto.LoginOrCreateReq;
import com.campus.user.dto.UpdateProfileReq;
import com.campus.user.dto.UserBrief;
import com.campus.user.dto.UserVO;
import com.campus.user.dto.VerifyReq;
import com.campus.user.dto.VerifyResp;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtService jwtService;

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
            userMapper.insert(user);
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
        vo.setStudentNo(maskStudentNo(user.getStudentNo()));
        vo.setRealName(maskName(user.getRealName()));
        vo.setVerifyStatus(user.getVerifyStatus());
        vo.setVerifyRemark(user.getVerifyRemark());
        vo.setVerifyImg(user.getVerifyImg());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    public UserVO updateProfile(Long userId, UpdateProfileReq req) {
        User user = requireUser(userId);
        if (StringUtils.hasText(req.getNickname())) {
            user.setNickname(req.getNickname().trim());
        }
        if (StringUtils.hasText(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getBio() != null) {
            user.setBio(req.getBio());
        }
        userMapper.updateById(user);
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
        if (userMapper.countStudentNoUsed(req.getSchoolId(), req.getStudentNo(), userId) > 0) {
            throw new BizException("该学号已被其他账号认证，请联系管理员");
        }

        user.setSchoolId(req.getSchoolId());
        user.setStudentNo(req.getStudentNo().trim());
        user.setRealName(req.getRealName().trim());
        user.setVerifyImg(req.getVerifyImg());
        user.setVerifyStatus(autoApprove ? 2 : 1);
        user.setVerifyRemark("");
        userMapper.updateById(user);

        String token = jwtService.create(user.getId(), user.getSchoolId(), user.getVerifyStatus());
        String msg = autoApprove ? "认证成功，欢迎来到校园趣事" : "已提交认证，预计 1 个工作日内完成审核";
        return new VerifyResp(user.getVerifyStatus(), token, msg);
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

    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}

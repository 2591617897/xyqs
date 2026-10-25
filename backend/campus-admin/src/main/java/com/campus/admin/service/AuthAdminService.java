package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.admin.config.AdminContext;
import com.campus.admin.config.AdminJwtService;
import com.campus.admin.config.AdminPasswordUtil;
import com.campus.admin.dto.AdminUserInfo;
import com.campus.admin.dto.ChangePasswordReq;
import com.campus.admin.dto.LoginReq;
import com.campus.admin.dto.LoginResp;
import com.campus.admin.entity.AdminLoginLog;
import com.campus.admin.entity.AdminRole;
import com.campus.admin.entity.AdminUser;
import com.campus.admin.mapper.AdminLoginLogMapper;
import com.campus.admin.mapper.AdminRoleMapper;
import com.campus.admin.mapper.AdminUserMapper;
import com.campus.admin.mapper.AdminUserRoleMapper;
import com.campus.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 后台登录、登出、改密。
 *
 * <p>登录是唯一不需要令牌的入口（网关按 method + path 精确放行），
 * 所以账号风控只能由这里自己做：禁用校验、失败计数、超限锁定，且成功失败都写登录日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAdminService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminLoginLogMapper adminLoginLogMapper;
    private final AdminJwtService jwtService;

    @Value("${campus.admin.login.max-fail:5}")
    private int maxFail;

    @Value("${campus.admin.login.lock-minutes:15}")
    private long lockMinutes;

    public LoginResp login(LoginReq req) {
        String username = req.getUsername().trim();
        AdminUser user = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, username));
        if (user == null) {
            recordLog(username, null, false, "账号不存在");
            throw BizException.of("账号或密码错误");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            recordLog(username, user.getId(), false, "账号已锁定");
            throw BizException.forbidden("账号已锁定，请 " + lockMinutes + " 分钟后重试");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            recordLog(username, user.getId(), false, "账号禁用");
            throw BizException.forbidden("账号已禁用，请联系管理员");
        }
        if (!AdminPasswordUtil.md5Hex(req.getPassword(), user.getSalt()).equals(user.getPassword())) {
            int fails = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
            AdminUser patch = new AdminUser();
            patch.setId(user.getId());
            patch.setFailCount(fails);
            if (fails >= maxFail) {
                patch.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            }
            adminUserMapper.updateById(patch);
            recordLog(username, user.getId(), false, "密码错误");
            if (fails >= maxFail) {
                throw BizException.forbidden("密码错误次数过多，账号已锁定 " + lockMinutes + " 分钟");
            }
            throw BizException.of("账号或密码错误，还可尝试 " + (maxFail - fails) + " 次");
        }

        AdminUser patch = new AdminUser();
        patch.setId(user.getId());
        patch.setFailCount(0);
        patch.setLockedUntil(null);
        patch.setLastLoginAt(LocalDateTime.now());
        patch.setLastLoginIp(clientIp());
        adminUserMapper.updateById(patch);
        recordLog(username, user.getId(), true, null);

        boolean superAdmin = Integer.valueOf(1).equals(user.getSuperFlag());
        String scope = Integer.valueOf(1).equals(user.getDataScope()) ? "ALL" : "REGION";
        String token = jwtService.create(user.getId(), user.getUsername(), superAdmin, scope, List.of());
        return new LoginResp(token, jwtService.expireHours(),
                Integer.valueOf(1).equals(user.getMustChangePwd()));
    }

    /**
     * 修改自己的密码。成功后清掉「必须改初始密码」标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordReq req) {
        Long adminId = AdminContext.requireAdminId();
        AdminUser user = adminUserMapper.selectById(adminId);
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        if (!AdminPasswordUtil.md5Hex(req.getOldPassword(), user.getSalt()).equals(user.getPassword())) {
            throw BizException.of("原密码不正确");
        }
        if (req.getOldPassword().equals(req.getNewPassword())) {
            throw BizException.of("新密码不能与原密码相同");
        }
        AdminUser patch = new AdminUser();
        patch.setId(adminId);
        patch.setPassword(AdminPasswordUtil.md5Hex(req.getNewPassword(), user.getSalt()));
        patch.setMustChangePwd(0);
        adminUserMapper.updateById(patch);
    }

    /**
     * 当前登录管理员信息 + 角色 + 权限点。权限点超管返回空集合（拦截器对超管直接放行）。
     */
    public AdminUserInfo info() {
        AdminContext.AdminUser ctx = AdminContext.get();
        if (ctx == null) {
            throw BizException.unauthorized();
        }
        AdminUser user = adminUserMapper.selectById(ctx.adminId());
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        AdminUserInfo info = new AdminUserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setRealName(user.getRealName());
        info.setAvatar(user.getAvatar());
        info.setSuperFlag(Integer.valueOf(1).equals(user.getSuperFlag()));
        info.setDataScope(user.getDataScope());
        info.setMustChangePwd(Integer.valueOf(1).equals(user.getMustChangePwd()));
        info.setLastLoginAt(user.getLastLoginAt());
        info.setRoles(rolesOf(user.getId()));
        info.setPermissions(ctx.superAdmin() ? Set.of() : ctx.permissions());
        return info;
    }

    public List<AdminUserInfo.RoleBrief> rolesOf(Long adminId) {
        List<Long> roleIds = adminUserRoleMapper.selectRoleIdsByAdminId(adminId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<AdminRole> roles = adminRoleMapper.selectBatchIds(roleIds);
        List<AdminUserInfo.RoleBrief> briefs = new ArrayList<>(roles.size());
        for (AdminRole role : roles) {
            AdminUserInfo.RoleBrief b = new AdminUserInfo.RoleBrief();
            b.setId(role.getId());
            b.setName(role.getName());
            b.setCode(role.getCode());
            briefs.add(b);
        }
        return briefs;
    }

    private void recordLog(String username, Long adminId, boolean success, String failReason) {
        AdminLoginLog record = new AdminLoginLog();
        record.setUsername(username);
        record.setAdminId(adminId);
        record.setIp(clientIp());
        record.setUa(clientUa());
        record.setStatus(success ? 1 : 0);
        record.setFailReason(failReason);
        try {
            adminLoginLogMapper.insert(record);
        } catch (Exception e) {
            // 日志写不进去不能反过来影响登录本身
            log.warn("登录日志写入失败 {}", e.getMessage());
        }
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private String clientUa() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String ua = attrs.getRequest().getHeader("User-Agent");
            return ua == null ? null : (ua.length() <= 500 ? ua : ua.substring(0, 500));
        }
        return null;
    }
}

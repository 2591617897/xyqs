package com.campus.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.config.AdminAuthInterceptor;
import com.campus.admin.config.AdminContext;
import com.campus.admin.config.AdminPasswordUtil;
import com.campus.admin.dto.AdminUserDTO;
import com.campus.admin.dto.AdminUserReq;
import com.campus.admin.dto.AdminAuthState;
import com.campus.admin.entity.AdminUser;
import com.campus.admin.entity.AdminUserRole;
import com.campus.admin.mapper.AdminMenuMapper;
import com.campus.admin.mapper.AdminUserMapper;
import com.campus.admin.mapper.AdminUserRoleMapper;
import com.campus.common.search.SearchKit;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理员账号维护。
 *
 * <p>这里有几条「超管也不能绕过」的保护规则，刻意写在 Service 里而不是权限注解里：
 * 不能禁用/删除自己、不能删除超级管理员、不能把自己的 super_flag 改成 0。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminMenuMapper adminMenuMapper;

    public R.Page<AdminUserDTO> page(String keyword, Long roleId, Integer status, long page, long size) {
        LambdaQueryWrapper<AdminUser> qw = Wrappers.<AdminUser>lambdaQuery()
                .eq(status != null, AdminUser::getStatus, status)
                .orderByDesc(AdminUser::getCreatedAt);
        // 账号 / 姓名 / 手机号三列各自参与多词匹配
        SearchKit.andAllWords(qw, keyword, AdminUser::getUsername, AdminUser::getRealName, AdminUser::getPhone);
        Page<AdminUser> p = adminUserMapper.selectPage(new Page<>(page, size), qw);
        List<AdminUserDTO> list = new ArrayList<>(p.getRecords().size());
        for (AdminUser u : p.getRecords()) {
            AdminUserDTO dto = toDto(u);
            dto.setRoleIds(adminUserRoleMapper.selectRoleIdsByAdminId(u.getId()));
            list.add(dto);
        }
        // roleId 过滤放在内存里做：管理员量级最多几十个，不值得为它写 join
        if (roleId != null) {
            list.removeIf(dto -> dto.getRoleIds() == null || !dto.getRoleIds().contains(roleId));
        }
        return R.Page.of(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    public AdminUserDTO detail(Long id) {
        AdminUser u = adminUserMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("管理员不存在");
        }
        AdminUserDTO dto = toDto(u);
        dto.setRoleIds(adminUserRoleMapper.selectRoleIdsByAdminId(id));
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminUserReq req) {
        assertUsernameFree(req.getUsername(), null);
        AdminUser u = new AdminUser();
        u.setUsername(req.getUsername().trim());
        // BCrypt 自带盐，salt 列留空（列非空，给空串）。初始密码仍然要求首次登录后修改。
        u.setSalt("");
        String pwd = requirePassword(req);
        u.setPassword(AdminPasswordUtil.hash(pwd));
        fillBasic(u, req);
        u.setSuperFlag(0);
        u.setMustChangePwd(1);
        u.setFailCount(0);
        adminUserMapper.insert(u);
        replaceRoles(u.getId(), req.getRoleIds());
        return u.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminUserReq req) {
        AdminUser old = adminUserMapper.selectById(id);
        if (old == null) {
            throw BizException.notFound("管理员不存在");
        }
        assertUsernameFree(req.getUsername(), id);
        AdminUser u = new AdminUser();
        u.setId(id);
        u.setUsername(req.getUsername().trim());
        fillBasic(u, req);
        // 以下几个字段不允许通过编辑改：超级管理员标记 / superFlag / dataScope 由超级管理员在专属接口维护
        u.setSuperFlag(old.getSuperFlag());
        if (StringUtils.hasText(req.getPassword())) {
            String weak = AdminPasswordUtil.checkStrength(req.getPassword(), req.getUsername());
            if (weak != null) {
                throw BizException.of(weak);
            }
            u.setPassword(AdminPasswordUtil.hash(req.getPassword()));
            u.setSalt("");
            u.setMustChangePwd(1);
        }
        // 编辑自己时不允许把自己禁用
        Long self = AdminContext.adminId();
        if (self != null && self.equals(id) && req.getStatus() != null && req.getStatus() != 1) {
            throw BizException.of("不能禁用自己的账号");
        }
        boolean pwdChanged = StringUtils.hasText(req.getPassword());
        adminUserMapper.updateById(u);
        replaceRoles(id, req.getRoleIds());
        AdminAuthInterceptor.evictPermissionCache(id);
        // 管理员被改了口令 / 被禁用，都必须让旧令牌立刻失效
        if (pwdChanged || (req.getStatus() != null && req.getStatus() != 1)) {
            revokeTokens(id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AdminUser old = adminUserMapper.selectById(id);
        if (old == null) {
            throw BizException.notFound("管理员不存在");
        }
        Long self = AdminContext.adminId();
        if (self != null && self.equals(id)) {
            throw BizException.of("不能删除自己的账号");
        }
        if (Integer.valueOf(1).equals(old.getSuperFlag())) {
            throw BizException.of("超级管理员不可删除");
        }
        adminUserRoleMapper.delete(Wrappers.<AdminUserRole>lambdaQuery().eq(AdminUserRole::getAdminUserId, id));
        adminUserMapper.deleteById(id);
        AdminAuthInterceptor.evictPermissionCache(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        AdminUser old = adminUserMapper.selectById(id);
        if (old == null) {
            throw BizException.notFound("管理员不存在");
        }
        Long self = AdminContext.adminId();
        if (self != null && self.equals(id) && status != null && status != 1) {
            throw BizException.of("不能禁用自己的账号");
        }
        AdminUser u = new AdminUser();
        u.setId(id);
        u.setStatus(status);
        adminUserMapper.updateById(u);
        AdminAuthInterceptor.evictPermissionCache(id);
        if (status != null && status != 1) {
            revokeTokens(id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        AdminUser old = adminUserMapper.selectById(id);
        if (old == null) {
            throw BizException.notFound("管理员不存在");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
            throw BizException.of("新密码长度不能少于 8 位");
        }
        String weak = AdminPasswordUtil.checkStrength(newPassword, old.getUsername());
        if (weak != null) {
            throw BizException.of(weak);
        }
        AdminUser u = new AdminUser();
        u.setId(id);
        u.setPassword(AdminPasswordUtil.hash(newPassword));
        u.setSalt("");
        u.setMustChangePwd(1);
        u.setFailCount(0);
        u.setLockedUntil(null);
        adminUserMapper.updateById(u);
        // 管理员重置别人的密码后，对方手上的旧令牌必须立刻作废
        revokeTokens(id);
    }

    /**
     * 某管理员拥有的全部权限点。被 {@link AdminAuthInterceptor} 调用（带本地缓存）。
     */
    public Set<String> permissionsOf(Long adminId) {
        return new HashSet<>(adminMenuMapper.selectPermissionsByAdminId(adminId));
    }

    /**
     * 鉴权状态快照（状态 / 令牌版本 / 是否待改密）。被拦截器调用，10 秒本地缓存。
     */
    public AdminAuthState authStateOf(Long adminId) {
        return adminUserMapper.selectAuthState(adminId);
    }

    /**
     * 让某管理员的全部已发出令牌失效。
     *
     * <p>改密、重置密码、禁用、删除账号时必须调用 —— 否则旧令牌在有效期内（8 小时）
     * 仍然能继续操作，这正是「改完密码别人还能进」的根因。
     */
    public void revokeTokens(Long adminId) {
        adminUserMapper.bumpTokenVersion(adminId);
        AdminAuthInterceptor.evictPermissionCache(adminId);
        log.info("[security] 管理员 {} 的令牌已全部失效（token_version +1）", adminId);
    }

    /**
     * 重新绑定角色。先按 admin_user_id 全删再批量插，
     * 避免 uk_user_role 把 DuplicateKeyException 冒成 500 那种看不懂的报错。
     */
    private void replaceRoles(Long adminId, List<Long> roleIds) {
        adminUserRoleMapper.delete(Wrappers.<AdminUserRole>lambdaQuery()
                .eq(AdminUserRole::getAdminUserId, adminId));
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds) {
            AdminUserRole ur = new AdminUserRole();
            ur.setAdminUserId(adminId);
            ur.setRoleId(roleId);
            ur.setCreatedAt(now);
            adminUserRoleMapper.insert(ur);
        }
    }

    private void assertUsernameFree(String username, Long excludeId) {
        AdminUser exist = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, username.trim()));
        if (exist != null && (excludeId == null || !excludeId.equals(exist.getId()))) {
            throw BizException.of("账号已存在");
        }
    }

    private void fillBasic(AdminUser u, AdminUserReq req) {
        u.setRealName(req.getRealName());
        u.setPhone(req.getPhone());
        u.setEmail(req.getEmail());
        u.setDataScope(req.getDataScope() == null ? 1 : req.getDataScope());
        u.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        u.setRemark(req.getRemark());
    }

    private String requirePassword(AdminUserReq req) {
        if (!StringUtils.hasText(req.getPassword())) {
            throw BizException.of("请设置初始密码");
        }
        String weak = AdminPasswordUtil.checkStrength(req.getPassword(), req.getUsername());
        if (weak != null) {
            throw BizException.of(weak);
        }
        return req.getPassword();
    }

    /** 保留给历史 MD5 记录使用；新密码一律 BCrypt（自带盐），不再需要单独生成 */
    @Deprecated
    private String newSalt() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private AdminUserDTO toDto(AdminUser u) {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setRealName(u.getRealName());
        dto.setPhone(u.getPhone());
        dto.setEmail(u.getEmail());
        dto.setAvatar(u.getAvatar());
        dto.setSuperFlag(u.getSuperFlag());
        dto.setDataScope(u.getDataScope());
        dto.setStatus(u.getStatus());
        dto.setMustChangePwd(u.getMustChangePwd());
        dto.setLastLoginAt(u.getLastLoginAt());
        dto.setLastLoginIp(u.getLastLoginIp());
        dto.setRemark(u.getRemark());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }

}

package com.campus.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.config.AdminAuthInterceptor;
import com.campus.admin.dto.AdminRoleReq;
import com.campus.admin.entity.AdminRole;
import com.campus.admin.entity.AdminRoleMenu;
import com.campus.admin.entity.AdminUserRole;
import com.campus.admin.mapper.AdminRoleMapper;
import com.campus.admin.mapper.AdminRoleMenuMapper;
import com.campus.admin.mapper.AdminUserRoleMapper;
import com.campus.common.search.SearchKit;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色维护与授权。
 *
 * <p>内置 {@code SUPER_ADMIN} 角色不可删除、不可改 code —— 这是唯一一个
 * 不靠权限点、而靠 Service 硬编码保护的对象：万一有人把超管的菜单全撤了，
 * 拦截器对 super_flag=1 仍然放行，不会把自己锁死在门外。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;

    public R.Page<AdminRole> page(String keyword, Integer status, long page, long size) {
        LambdaQueryWrapper<AdminRole> qw = Wrappers.<AdminRole>lambdaQuery()
                .eq(status != null, AdminRole::getStatus, status)
                .orderByDesc(AdminRole::getSort);
        SearchKit.andAllWords(qw, keyword, AdminRole::getName, AdminRole::getCode);
        Page<AdminRole> p = adminRoleMapper.selectPage(new Page<>(page, size), qw);
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public AdminRole detail(Long id) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色不存在");
        }
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminRoleReq req) {
        assertCodeFree(req.getCode(), null);
        AdminRole role = new AdminRole();
        role.setName(req.getName().trim());
        role.setCode(req.getCode().trim());
        role.setRemark(req.getRemark());
        role.setSort(req.getSort() == null ? 0 : req.getSort());
        role.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        adminRoleMapper.insert(role);
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminRoleReq req) {
        AdminRole old = adminRoleMapper.selectById(id);
        if (old == null) {
            throw BizException.notFound("角色不存在");
        }
        if (SUPER_ADMIN_CODE.equals(old.getCode()) && !SUPER_ADMIN_CODE.equals(req.getCode().trim())) {
            throw BizException.of("内置超级管理员角色的编码不可修改");
        }
        assertCodeFree(req.getCode(), id);
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setName(req.getName().trim());
        role.setCode(req.getCode().trim());
        role.setRemark(req.getRemark());
        role.setSort(req.getSort() == null ? 0 : req.getSort());
        role.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        adminRoleMapper.updateById(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AdminRole old = adminRoleMapper.selectById(id);
        if (old == null) {
            throw BizException.notFound("角色不存在");
        }
        if (SUPER_ADMIN_CODE.equals(old.getCode())) {
            throw BizException.of("内置超级管理员角色不可删除");
        }
        long used = adminUserRoleMapper.countByRoleId(id);
        if (used > 0) {
            throw BizException.of("该角色下还有 " + used + " 个管理员，请先解除绑定");
        }
        adminRoleMenuMapper.delete(Wrappers.<AdminRoleMenu>lambdaQuery().eq(AdminRoleMenu::getRoleId, id));
        adminRoleMapper.deleteById(id);
    }

    public List<Long> menuIdsOf(Long roleId) {
        return adminRoleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 角色授权。先全删再批量插，既幂等又不会撞 uk_role_menu。
     * 之后清掉所有绑定该角色的管理员的权限缓存，让授权即时生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void grantMenus(Long roleId, List<Long> menuIds) {
        if (adminRoleMapper.selectById(roleId) == null) {
            throw BizException.notFound("角色不存在");
        }
        adminRoleMenuMapper.delete(Wrappers.<AdminRoleMenu>lambdaQuery().eq(AdminRoleMenu::getRoleId, roleId));
        if (!CollectionUtils.isEmpty(menuIds)) {
            LocalDateTime now = LocalDateTime.now();
            for (Long menuId : menuIds) {
                AdminRoleMenu rm = new AdminRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                rm.setCreatedAt(now);
                adminRoleMenuMapper.insert(rm);
            }
        }
        for (Long adminId : adminIdsOfRole(roleId)) {
            AdminAuthInterceptor.evictPermissionCache(adminId);
        }
    }

    private List<Long> adminIdsOfRole(Long roleId) {
        List<AdminUserRole> rows = adminUserRoleMapper.selectList(
                Wrappers.<AdminUserRole>lambdaQuery().eq(AdminUserRole::getRoleId, roleId));
        return rows.stream().map(AdminUserRole::getAdminUserId).distinct().toList();
    }

    private void assertCodeFree(String code, Long excludeId) {
        AdminRole exist = adminRoleMapper.selectOne(Wrappers.<AdminRole>lambdaQuery()
                .eq(AdminRole::getCode, code.trim()));
        if (exist != null && (excludeId == null || !excludeId.equals(exist.getId()))) {
            throw BizException.of("角色编码已存在");
        }
    }
}

package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.admin.config.AdminAuthInterceptor;
import com.campus.admin.config.AdminContext;
import com.campus.admin.dto.AdminMenuReq;
import com.campus.admin.dto.MenuNode;
import com.campus.admin.entity.AdminMenu;
import com.campus.admin.entity.AdminRoleMenu;
import com.campus.admin.entity.AdminUserRole;
import com.campus.admin.mapper.AdminMenuMapper;
import com.campus.admin.mapper.AdminRoleMenuMapper;
import com.campus.admin.mapper.AdminUserRoleMapper;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单与权限点维护，以及「当前管理员可见菜单树」。
 *
 * <p>前端菜单路由完全由这里下发的数据驱动：component 字段对应
 * admin-web/src/views 下的 .vue 路径，两边必须一一对应。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final AdminMenuMapper adminMenuMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;

    /**
     * 全量菜单树（管理页用，含按钮节点）。
     */
    public List<MenuNode> tree(Integer type) {
        List<AdminMenu> all = adminMenuMapper.selectList(Wrappers.<AdminMenu>lambdaQuery()
                .eq(type != null, AdminMenu::getType, type)
                .orderByDesc(AdminMenu::getSort));
        return buildTree(all);
    }

    /**
     * 当前管理员可见菜单树（只含目录与菜单，不含按钮）。
     * 超级管理员返回全部启用菜单；普通管理员走三表 join。
     */
    public List<MenuNode> treeOfCurrentAdmin() {
        AdminContext.AdminUser ctx = AdminContext.get();
        if (ctx == null) {
            throw BizException.unauthorized();
        }
        List<AdminMenu> menus;
        if (ctx.superAdmin()) {
            menus = adminMenuMapper.selectList(Wrappers.<AdminMenu>lambdaQuery()
                    .eq(AdminMenu::getStatus, 1)
                    .in(AdminMenu::getType, List.of(1, 2))
                    .orderByDesc(AdminMenu::getSort));
        } else {
            menus = adminMenuMapper.selectMenusByAdminId(ctx.adminId());
        }
        return buildTree(menus);
    }

    public AdminMenu detail(Long id) {
        AdminMenu menu = adminMenuMapper.selectById(id);
        if (menu == null) {
            throw BizException.notFound("菜单不存在");
        }
        return menu;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminMenuReq req) {
        validate(req, null);
        AdminMenu menu = new AdminMenu();
        fill(menu, req);
        adminMenuMapper.insert(menu);
        return menu.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminMenuReq req) {
        AdminMenu old = adminMenuMapper.selectById(id);
        if (old == null) {
            throw BizException.notFound("菜单不存在");
        }
        validate(req, id);
        if (req.getParentId() != null && req.getParentId() > 0 && isDescendant(req.getParentId(), id)) {
            throw BizException.of("上级菜单不能是自己的下级");
        }
        AdminMenu menu = new AdminMenu();
        menu.setId(id);
        fill(menu, req);
        adminMenuMapper.updateById(menu);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        long children = adminMenuMapper.selectCount(Wrappers.<AdminMenu>lambdaQuery()
                .eq(AdminMenu::getParentId, id));
        if (children > 0) {
            throw BizException.of("该菜单下还有 " + children + " 个子项，请先删除子项");
        }
        adminRoleMenuMapper.delete(Wrappers.<AdminRoleMenu>lambdaQuery().eq(AdminRoleMenu::getMenuId, id));
        adminMenuMapper.deleteById(id);
        evictCacheByMenu(id);
    }

    /**
     * 一次性把扁平菜单列表组装成树。
     *
     * <p>先在内存里按 parentId 分组，再把「找不到父节点的」当顶层，
     * 最后一层深拷贝进 children —— 这样即使库里有孤儿节点（父被删了但没清干净），
     * 也不会整棵树丢掉。
     */
    private List<MenuNode> buildTree(List<AdminMenu> list) {
        Map<Long, MenuNode> nodeMap = new HashMap<>();
        List<MenuNode> roots = new ArrayList<>();
        for (AdminMenu m : list) {
            MenuNode n = toNode(m);
            nodeMap.put(m.getId(), n);
        }
        for (AdminMenu m : list) {
            MenuNode n = nodeMap.get(m.getId());
            MenuNode parent = m.getParentId() == null || m.getParentId() == 0
                    ? null : nodeMap.get(m.getParentId());
            if (parent == null) {
                roots.add(n);
            } else {
                parent.getChildren().add(n);
            }
        }
        roots.sort(Comparator.comparing(MenuNode::getSort, Comparator.reverseOrder()));
        for (MenuNode n : nodeMap.values()) {
            n.getChildren().sort(Comparator.comparing(MenuNode::getSort, Comparator.reverseOrder()));
        }
        return roots;
    }

    private MenuNode toNode(AdminMenu m) {
        MenuNode n = new MenuNode();
        n.setId(m.getId());
        n.setParentId(m.getParentId());
        n.setName(m.getName());
        n.setType(m.getType());
        n.setPath(m.getPath());
        n.setComponent(m.getComponent());
        n.setIcon(m.getIcon());
        n.setPermission(m.getPermission());
        n.setSort(m.getSort());
        n.setVisible(m.getVisible());
        n.setStatus(m.getStatus());
        return n;
    }

    private void fill(AdminMenu menu, AdminMenuReq req) {
        menu.setParentId(req.getParentId() == null ? 0 : req.getParentId());
        menu.setName(req.getName().trim());
        menu.setType(req.getType() == null ? 2 : req.getType());
        menu.setPath(req.getPath());
        menu.setComponent(req.getComponent());
        menu.setIcon(req.getIcon());
        menu.setPermission(StringUtils.hasText(req.getPermission()) ? req.getPermission().trim() : null);
        menu.setSort(req.getSort() == null ? 0 : req.getSort());
        menu.setVisible(req.getVisible() == null ? 1 : req.getVisible());
        menu.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        menu.setRemark(req.getRemark());
    }

    private void validate(AdminMenuReq req, Long excludeId) {
        if (Integer.valueOf(3).equals(req.getType()) && !StringUtils.hasText(req.getPermission())) {
            throw BizException.of("按钮类型必须填写权限点");
        }
        if (!StringUtils.hasText(req.getPermission())) {
            return;
        }
        AdminMenu exist = adminMenuMapper.selectOne(Wrappers.<AdminMenu>lambdaQuery()
                .eq(AdminMenu::getPermission, req.getPermission().trim()));
        if (exist != null && (excludeId == null || !excludeId.equals(exist.getId()))) {
            throw BizException.of("权限点已存在：" + req.getPermission());
        }
    }

    /** 判断 candidate 是否是 ancestor 的子孙 */
    private boolean isDescendant(Long candidate, Long ancestor) {
        Long cursor = candidate;
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 20) {
            if (cursor.equals(ancestor)) {
                return true;
            }
            AdminMenu m = adminMenuMapper.selectById(cursor);
            if (m == null) {
                return false;
            }
            cursor = m.getParentId();
        }
        return false;
    }

    /** 菜单被删除/改动后，清掉所有用到它的管理员权限缓存 */
    private void evictCacheByMenu(Long menuId) {
        List<AdminRoleMenu> rows = adminRoleMenuMapper.selectList(
                Wrappers.<AdminRoleMenu>lambdaQuery().eq(AdminRoleMenu::getMenuId, menuId));
        for (AdminRoleMenu rm : rows) {
            List<AdminUserRole> urs = adminUserRoleMapper.selectList(
                    Wrappers.<AdminUserRole>lambdaQuery().eq(AdminUserRole::getRoleId, rm.getRoleId()));
            for (AdminUserRole ur : urs) {
                AdminAuthInterceptor.evictPermissionCache(ur.getAdminUserId());
            }
        }
    }
}

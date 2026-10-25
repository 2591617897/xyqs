package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单与权限点。
 *
 * <p>权限查询是一次三表 join，被 {@code AdminAuthInterceptor} 按 60 秒本地缓存调用，
 * 所以这里不用再叠加缓存。
 */
@Mapper
public interface AdminMenuMapper extends BaseMapper<AdminMenu> {

    /**
     * 某管理员拥有的全部权限点（去重）。超级管理员不会走到这里（拦截器直接放行）。
     */
    @Select("SELECT DISTINCT m.permission FROM t_admin_menu m "
            + "JOIN t_admin_role_menu rm ON rm.menu_id = m.id "
            + "JOIN t_admin_user_role ur ON ur.role_id = rm.role_id "
            + "WHERE ur.admin_user_id = #{adminId} AND m.status = 1 "
            + "AND m.permission IS NOT NULL AND m.permission <> ''")
    List<String> selectPermissionsByAdminId(@Param("adminId") Long adminId);

    /**
     * 某管理员可见的菜单（type=1 目录 / type=2 菜单）。
     * 注意：不要让 type=3 的按钮行混进来，否则前端会把按钮渲染成菜单。
     */
    @Select("SELECT DISTINCT m.* FROM t_admin_menu m "
            + "JOIN t_admin_role_menu rm ON rm.menu_id = m.id "
            + "JOIN t_admin_user_role ur ON ur.role_id = rm.role_id "
            + "WHERE ur.admin_user_id = #{adminId} AND m.status = 1 AND m.type IN (1, 2) "
            + "ORDER BY m.sort DESC")
    List<AdminMenu> selectMenusByAdminId(@Param("adminId") Long adminId);
}

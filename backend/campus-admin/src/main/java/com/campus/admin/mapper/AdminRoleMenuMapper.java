package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-菜单权限关联。
 *
 * <p>重新授权统一走「先按 role_id 删、再批量插」，由 uk_role_menu 兜底幂等。
 */
@Mapper
public interface AdminRoleMenuMapper extends BaseMapper<AdminRoleMenu> {

    @Select("SELECT menu_id FROM t_admin_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}

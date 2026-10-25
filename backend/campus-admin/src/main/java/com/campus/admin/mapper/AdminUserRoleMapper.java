package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 管理员-角色关联。
 *
 * <p>重新授权统一走「先按 admin_user_id 删、再批量插」，
 * 避免 uk_user_role 把 DuplicateKeyException 冒成 500。
 */
@Mapper
public interface AdminUserRoleMapper extends BaseMapper<AdminUserRole> {

    @Select("SELECT role_id FROM t_admin_user_role WHERE admin_user_id = #{adminId}")
    List<Long> selectRoleIdsByAdminId(@Param("adminId") Long adminId);

    @Select("SELECT COUNT(*) FROM t_admin_user_role WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);
}

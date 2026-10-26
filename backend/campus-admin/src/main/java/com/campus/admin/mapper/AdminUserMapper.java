package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.dto.AdminAuthState;
import com.campus.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 管理员。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    /**
     * 鉴权状态快照：只取三个小字段，走主键索引，不要换成 selectById（会把哈希也读出来）。
     */
    @Select("select status, token_version as tokenVersion, must_change_pwd as mustChangePwd "
            + "from t_admin_user where id = #{id}")
    AdminAuthState selectAuthState(@Param("id") Long id);

    /**
     * 令牌版本自增。用 SQL 自增而不是「先读再写」，避免并发改密时相互覆盖。
     */
    @Update("update t_admin_user set token_version = token_version + 1 where id = #{id}")
    int bumpTokenVersion(@Param("id") Long id);
}

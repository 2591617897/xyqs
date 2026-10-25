package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}

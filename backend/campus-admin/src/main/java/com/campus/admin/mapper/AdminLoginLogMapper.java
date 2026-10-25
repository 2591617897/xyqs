package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志。
 */
@Mapper
public interface AdminLoginLogMapper extends BaseMapper<AdminLoginLog> {
}

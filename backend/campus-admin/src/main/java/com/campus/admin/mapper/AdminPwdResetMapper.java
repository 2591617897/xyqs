package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminPwdReset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 找回密码验证码。
 */
@Mapper
public interface AdminPwdResetMapper extends BaseMapper<AdminPwdReset> {
}

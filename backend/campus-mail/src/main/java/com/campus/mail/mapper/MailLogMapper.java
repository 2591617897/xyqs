package com.campus.mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mail.entity.MailLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发信日志。
 */
@Mapper
public interface MailLogMapper extends BaseMapper<MailLog> {
}

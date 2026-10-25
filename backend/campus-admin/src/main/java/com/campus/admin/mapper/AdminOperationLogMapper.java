package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志。写入走 {@code AsyncAuditWriter} 的批量通道，不走逐条 insert。
 */
@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {
}

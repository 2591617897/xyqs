package com.campus.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.auth.entity.ContentAuditTrail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容处置流水（t_content_audit），本服务只做插入。
 */
@Mapper
public interface ContentAuditTrailMapper extends BaseMapper<ContentAuditTrail> {
}

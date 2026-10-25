package com.campus.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.auth.entity.MediaCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 图片审核台账。
 */
@Mapper
public interface MediaCheckMapper extends BaseMapper<MediaCheck> {

    /** 回调只带 trace_id，按它反查台账 */
    @Select("SELECT * FROM t_media_check WHERE trace_id = #{traceId} LIMIT 1")
    MediaCheck selectByTraceId(@Param("traceId") String traceId);
}

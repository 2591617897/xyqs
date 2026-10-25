package com.campus.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.auth.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 敏感词只读 Mapper。
 *
 * <p>唯一一处写操作是 {@link #incrHit}：命中计数用于运营复盘「哪些词真的在拦人」，
 * 但它是<b>尽力而为</b>的统计——失败只打日志，绝不影响发布。
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

    /** 载入全部启用中的词，按长度倒序（长词优先匹配，避免「习近平」被「习」先替换掉） */
    @Select("SELECT id, word, level, replace_to, category, status, hit_count "
            + "FROM t_sensitive_word WHERE status = 1 ORDER BY CHAR_LENGTH(word) DESC")
    List<SensitiveWord> selectEnabled();

    @Update("UPDATE t_sensitive_word SET hit_count = hit_count + 1 WHERE id = #{id}")
    int incrHit(@Param("id") Long id);
}

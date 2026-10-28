package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.DemandRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 校园需求跨表只读查询（t_demand + t_school）。
 */
@Mapper
public interface DemandQueryMapper {

    @Select("<script>"
            + "SELECT d.id, d.type, d.title, d.description, d.category_id categoryId, d.budget, "
            + "       d.expect_time expectTime, d.location, d.images, "
            + "       d.user_id userId, d.author_name authorName, d.school_id schoolId, s.name schoolName, "
            + "       d.visibility, d.status, d.view_count viewCount, d.reply_count replyCount, d.created_at createdAt "
            + "FROM t_demand d "
            + "LEFT JOIN t_school s ON s.id = d.school_id "
            + "WHERE 1 = 1 "
            + "<if test='keywords != null and keywords.size() &gt; 0'>"
            + "  <foreach collection='keywords' item='kw'>"
            + "    AND (d.title LIKE CONCAT('%', #{kw}, '%') OR d.description LIKE CONCAT('%', #{kw}, '%')) "
            + "  </foreach>"
            + "</if>"
            + "<if test='type != null'>AND d.type = #{type} </if>"
            + "<if test='status != null'>AND d.status = #{status} </if>"
            + "<if test='userId != null'>AND d.user_id = #{userId} </if>"
            + "<if test='schoolIds != null and schoolIds.size() &gt; 0'>"
            + "  AND d.school_id IN <foreach collection='schoolIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
            + "</if>"
            + "ORDER BY d.id DESC"
            + "</script>")
    IPage<DemandRow> selectDemands(IPage<DemandRow> page,
                                   @Param("keywords") List<String> keywords,
                                   @Param("type") Integer type,
                                   @Param("status") Integer status,
                                   @Param("userId") Long userId,
                                   @Param("schoolIds") List<Long> schoolIds);

    @Select("SELECT d.id, d.type, d.title, d.description, d.category_id categoryId, d.budget, "
            + "       d.expect_time expectTime, d.location, d.images, "
            + "       d.user_id userId, d.author_name authorName, d.school_id schoolId, s.name schoolName, "
            + "       d.visibility, d.status, d.view_count viewCount, d.reply_count replyCount, d.created_at createdAt "
            + "FROM t_demand d "
            + "LEFT JOIN t_school s ON s.id = d.school_id "
            + "WHERE d.id = #{id}")
    DemandRow selectDemandOne(@Param("id") Long id);
}

package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.WallRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 表白墙跨表只读查询。
 *
 * <p>这里唯一会把「匿名展示名」与「真实作者」放在同一行的地方。
 * realUserId 只应出现在后台响应里，前台接口绝不能复用本 Mapper。
 */
@Mapper
public interface WallQueryMapper {

    @Select("<script>"
            + "SELECT w.id, w.content, w.images, w.school_id schoolId, s.name schoolName, w.category, "
            + "       w.anon_name anonName, w.anon_avatar anonAvatar, w.user_id realUserId, "
            + "       u.nickname realNickname, w.like_count likeCount, w.comment_count commentCount, "
            + "       w.status, w.created_at createdAt "
            + "FROM t_wall_post w "
            + "LEFT JOIN t_school s ON s.id = w.school_id "
            + "LEFT JOIN t_user u ON u.id = w.user_id "
            + "WHERE 1 = 1 "
            + "<if test='keywords != null and keywords.size() &gt; 0'>"
            + "  <foreach collection='keywords' item='kw'>"
            + "    AND (w.content LIKE CONCAT('%', #{kw}, '%') OR w.anon_name LIKE CONCAT('%', #{kw}, '%')) "
            + "  </foreach>"
            + "</if>"
            + "<if test='status != null'>AND w.status = #{status} </if>"
            + "<if test='category != null and category != &apos;&apos;'>AND w.category = #{category} </if>"
            + "<if test='authorId != null'>AND w.user_id = #{authorId} </if>"
            + "<if test='schoolIds != null and schoolIds.size() &gt; 0'>"
            + "  AND w.school_id IN <foreach collection='schoolIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
            + "</if>"
            + "ORDER BY w.id DESC"
            + "</script>")
    IPage<WallRow> selectWall(IPage<WallRow> page,
                              @Param("keywords") List<String> keywords,
                              @Param("status") Integer status,
                              @Param("category") String category,
                              @Param("authorId") Long authorId,
                              @Param("schoolIds") List<Long> schoolIds);

    @Select("SELECT w.id, w.content, w.images, w.school_id schoolId, s.name schoolName, w.category, "
            + "       w.anon_name anonName, w.anon_avatar anonAvatar, w.user_id realUserId, "
            + "       u.nickname realNickname, w.like_count likeCount, w.comment_count commentCount, "
            + "       w.status, w.created_at createdAt "
            + "FROM t_wall_post w "
            + "LEFT JOIN t_school s ON s.id = w.school_id "
            + "LEFT JOIN t_user u ON u.id = w.user_id "
            + "WHERE w.id = #{id}")
    WallRow selectWallPost(@Param("id") Long id);
}

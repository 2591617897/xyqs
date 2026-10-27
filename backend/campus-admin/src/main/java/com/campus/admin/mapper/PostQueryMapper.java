package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.PostRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 帖子跨表只读查询。
 *
 * <p>后台不持有 t_post 实体（那是 campus-post 的领域对象），只读地 join 出展示行，
 * 写操作一律走 Feign /internal/admin/posts，失败才降级直写。这里刻意不提供任何 UPDATE。
 */
@Mapper
public interface PostQueryMapper {

    @Select("<script>"
            + "SELECT p.id, p.title, p.content, p.images, p.user_id authorId, p.author_name authorName, "
            + "       p.author_avatar authorAvatar, p.channel_id channelId, c.name channelName, "
            + "       p.school_id schoolId, s.name schoolName, p.like_count likeCount, "
            + "       p.comment_count commentCount, p.status, p.created_at createdAt "
            + "FROM t_post p "
            + "LEFT JOIN t_channel c ON c.id = p.channel_id "
            + "LEFT JOIN t_school s ON s.id = p.school_id "
            + "WHERE 1 = 1 "
            + "<if test='keywords != null and keywords.size() &gt; 0'>"
            + "  <foreach collection='keywords' item='kw'>"
            + "    AND (p.title LIKE CONCAT('%', #{kw}, '%') OR p.content LIKE CONCAT('%', #{kw}, '%')) "
            + "  </foreach>"
            + "</if>"
            + "<if test='status != null'>AND p.status = #{status} </if>"
            + "<if test='authorId != null'>AND p.user_id = #{authorId} </if>"
            + "<if test='schoolIds != null and schoolIds.size() &gt; 0'>"
            + "  AND p.school_id IN <foreach collection='schoolIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
            + "</if>"
            + "ORDER BY p.id DESC"
            + "</script>")
    IPage<PostRow> selectPosts(IPage<PostRow> page,
                               @Param("keywords") List<String> keywords,
                               @Param("status") Integer status,
                               @Param("authorId") Long authorId,
                               @Param("schoolIds") List<Long> schoolIds);

    @Select("SELECT p.id, p.title, p.content, p.images, p.user_id authorId, p.author_name authorName, "
            + "       p.author_avatar authorAvatar, p.channel_id channelId, c.name channelName, "
            + "       p.school_id schoolId, s.name schoolName, p.like_count likeCount, "
            + "       p.comment_count commentCount, p.status, p.created_at createdAt "
            + "FROM t_post p "
            + "LEFT JOIN t_channel c ON c.id = p.channel_id "
            + "LEFT JOIN t_school s ON s.id = p.school_id "
            + "WHERE p.id = #{id}")
    PostRow selectPost(@Param("id") Long id);
}

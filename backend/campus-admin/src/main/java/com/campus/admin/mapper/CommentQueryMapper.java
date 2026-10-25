package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.CommentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论跨表只读查询：帖子 / 表白墙 / 商品三类评论合并成一条时间线。
 *
 * <p>用 UNION ALL 而不是分别查三次再内存排序——后者要拉全表才能正确分页。
 * 外层包一层子查询是为了让 WHERE / ORDER BY 作用在合并后的结果集上。
 */
@Mapper
public interface CommentQueryMapper {

    @Select("<script>"
            + "SELECT t.* FROM ( "
            + "  SELECT c.id, 'P_COMMENT' targetType, c.post_id targetId, p.title targetTitle, "
            + "         c.user_id userId, c.user_name userName, c.content, c.images, c.created_at createdAt "
            + "  FROM t_post_comment c LEFT JOIN t_post p ON p.id = c.post_id "
            + "  UNION ALL "
            + "  SELECT c.id, 'W_COMMENT' targetType, c.post_id targetId, LEFT(w.content, 40) targetTitle, "
            + "         c.user_id userId, c.anon_name userName, c.content, c.images, c.created_at createdAt "
            + "  FROM t_wall_comment c LEFT JOIN t_wall_post w ON w.id = c.post_id "
            + "  UNION ALL "
            + "  SELECT c.id, 'G_COMMENT' targetType, c.goods_id targetId, g.title targetTitle, "
            + "         c.user_id userId, c.user_name userName, c.content, c.images, c.created_at createdAt "
            + "  FROM t_goods_comment c LEFT JOIN t_goods g ON g.id = c.goods_id "
            + ") t WHERE 1 = 1 "
            + "<if test='keyword != null and keyword != &apos;&apos;'>AND t.content LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "<if test='targetType != null and targetType != &apos;&apos;'>AND t.targetType = #{targetType} </if>"
            + "<if test='userId != null'>AND t.userId = #{userId} </if>"
            + "ORDER BY t.createdAt DESC"
            + "</script>")
    IPage<CommentRow> selectComments(IPage<CommentRow> page,
                                     @Param("keyword") String keyword,
                                     @Param("targetType") String targetType,
                                     @Param("userId") Long userId);
}

package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.GoodsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 二手商品跨表只读查询。
 */
@Mapper
public interface GoodsQueryMapper {

    @Select("<script>"
            + "SELECT g.id, g.title, g.description, g.price, g.original_price originalPrice, g.images, "
            + "       g.user_id sellerId, g.seller_name sellerName, g.school_id schoolId, s.name schoolName, "
            + "       g.category_id categoryId, g.condition_level conditionLevel, g.status, "
            + "       g.view_count viewCount, g.like_count likeCount, g.comment_count commentCount, "
            + "       g.created_at createdAt "
            + "FROM t_goods g "
            + "LEFT JOIN t_school s ON s.id = g.school_id "
            + "WHERE 1 = 1 "
            + "<if test='keywords != null and keywords.size() &gt; 0'>"
            + "  <foreach collection='keywords' item='kw'>"
            + "    AND (g.title LIKE CONCAT('%', #{kw}, '%') OR g.description LIKE CONCAT('%', #{kw}, '%')) "
            + "  </foreach>"
            + "</if>"
            + "<if test='status != null'>AND g.status = #{status} </if>"
            + "<if test='sellerId != null'>AND g.user_id = #{sellerId} </if>"
            + "<if test='schoolIds != null and schoolIds.size() &gt; 0'>"
            + "  AND g.school_id IN <foreach collection='schoolIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
            + "</if>"
            + "ORDER BY g.id DESC"
            + "</script>")
    IPage<GoodsRow> selectGoods(IPage<GoodsRow> page,
                                @Param("keywords") List<String> keywords,
                                @Param("status") Integer status,
                                @Param("sellerId") Long sellerId,
                                @Param("schoolIds") List<Long> schoolIds);

    @Select("SELECT g.id, g.title, g.description, g.price, g.original_price originalPrice, g.images, "
            + "       g.user_id sellerId, g.seller_name sellerName, g.school_id schoolId, s.name schoolName, "
            + "       g.category_id categoryId, g.condition_level conditionLevel, g.status, "
            + "       g.view_count viewCount, g.like_count likeCount, g.comment_count commentCount, "
            + "       g.created_at createdAt "
            + "FROM t_goods g "
            + "LEFT JOIN t_school s ON s.id = g.school_id "
            + "WHERE g.id = #{id}")
    GoodsRow selectGoodsOne(@Param("id") Long id);
}

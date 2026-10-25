package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.UserRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户跨表只读查询。
 *
 * <p>blacklisted / muted 用 EXISTS 子查询投影出来，列表页才能直接筛选和打标，
 * 否则「筛出所有被禁言的人」要先查一张处置表再回表，分页就废了。
 */
@Mapper
public interface UserQueryMapper {

    @Select("<script>"
            + "SELECT u.id, u.nickname, u.avatar, u.phone, u.gender, u.school_id schoolId, s.name schoolName, "
            + "       u.verify_status verifyStatus, u.status, u.mute_until muteUntil, "
            + "       u.fans_count fansCount, u.follow_count followCount, u.created_at createdAt, "
            + "       IF(EXISTS(SELECT 1 FROM t_user_blacklist b WHERE b.user_id = u.id AND b.status = 1), 1, 0) blacklisted, "
            + "       IF(EXISTS(SELECT 1 FROM t_user_mute m WHERE m.user_id = u.id AND m.status = 1 "
            + "                 AND (m.end_at IS NULL OR m.end_at &gt; NOW())), 1, 0) muted "
            + "FROM t_user u "
            + "LEFT JOIN t_school s ON s.id = u.school_id "
            + "WHERE 1 = 1 "
            + "<if test='keyword != null and keyword != &apos;&apos;'>"
            + "  AND (u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.phone LIKE CONCAT('%', #{keyword}, '%')) "
            + "</if>"
            + "<if test='schoolId != null'>AND u.school_id = #{schoolId} </if>"
            + "<if test='verifyStatus != null'>AND u.verify_status = #{verifyStatus} </if>"
            + "<if test='status != null'>AND u.status = #{status} </if>"
            + "<if test='muted != null and muted == 1'>AND EXISTS (SELECT 1 FROM t_user_mute m WHERE m.user_id = u.id AND m.status = 1 AND (m.end_at IS NULL OR m.end_at &gt; NOW())) </if>"
            + "<if test='blacklisted != null and blacklisted == 1'>AND EXISTS (SELECT 1 FROM t_user_blacklist b WHERE b.user_id = u.id AND b.status = 1) </if>"
            + "<if test='schoolIds != null and schoolIds.size() &gt; 0'>"
            + "  AND u.school_id IN <foreach collection='schoolIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
            + "</if>"
            + "ORDER BY u.id DESC"
            + "</script>")
    IPage<UserRow> selectUsers(IPage<UserRow> page,
                               @Param("keyword") String keyword,
                               @Param("schoolId") Long schoolId,
                               @Param("verifyStatus") Integer verifyStatus,
                               @Param("status") Integer status,
                               @Param("muted") Integer muted,
                               @Param("blacklisted") Integer blacklisted,
                               @Param("schoolIds") List<Long> schoolIds);

    /**
     * 单条详情。
     *
     * <p>即使没有任何动态标签，也必须套 {@code <script>}：MyBatis 只对带 script 的注解做 XML 实体解析，
     * 否则 {@code &gt;} 会原样拼进 SQL，MySQL 直接报语法错。列表那条同理，两条要保持一致的写法。
     */
    @Select("<script>"
            + "SELECT u.id, u.nickname, u.avatar, u.phone, u.gender, u.school_id schoolId, s.name schoolName, "
            + "       u.verify_status verifyStatus, u.status, u.mute_until muteUntil, u.bio, "
            + "       u.real_name realName, u.student_no studentNo, u.verify_img verifyImg, u.verify_remark verifyRemark, "
            + "       u.fans_count fansCount, u.follow_count followCount, u.created_at createdAt, "
            + "       IF(EXISTS(SELECT 1 FROM t_user_blacklist b WHERE b.user_id = u.id AND b.status = 1), 1, 0) blacklisted, "
            + "       IF(EXISTS(SELECT 1 FROM t_user_mute m WHERE m.user_id = u.id AND m.status = 1 "
            + "                 AND (m.end_at IS NULL OR m.end_at &gt; NOW())), 1, 0) muted "
            + "FROM t_user u LEFT JOIN t_school s ON s.id = u.school_id "
            + "WHERE u.id = #{id}"
            + "</script>")
    UserRow selectUser(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM t_post WHERE user_id = #{userId} AND status = 1")
    long countPosts(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_goods WHERE user_id = #{userId} AND status = 1")
    long countGoods(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_wall_post WHERE user_id = #{userId} AND status = 1")
    long countWall(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_goods_order WHERE buyer_id = #{userId} OR seller_id = #{userId}")
    long countOrders(@Param("userId") Long userId);
}

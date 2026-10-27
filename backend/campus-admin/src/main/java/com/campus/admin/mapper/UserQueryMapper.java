package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.UserRow;
import com.campus.common.crypto.EncryptedStringTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户跨表只读查询。
 *
 * <p>blacklisted / muted 用 EXISTS 子查询投影出来，列表页才能直接筛选和打标，
 * 否则「筛出所有被禁言的人」要先查一张处置表再回表，分页就废了。
 *
 * <p><b>加密列的处理</b>：t_user.phone / student_no / real_name 在库里是 AES-GCM 密文，
 * 这里必须显式声明 {@link EncryptedStringTypeHandler}，否则后台列表会直接展示
 * {@code enc:v1:...} 这种乱码。注意注解 SQL 里用了别名的列（realName / studentNo），
 * Result 的 column 要写别名而不是下划线列名，否则匹配不上。
 *
 * <p>相应的，手机号也不能再 {@code LIKE} 了 —— 密文里没有可读数字。
 * 搜索改成「昵称模糊 OR 手机号盲索引精确匹配」，见 {@code UserManageService#list}。
 */
@Mapper
public interface UserQueryMapper {

    @Results(id = "userRowBase", value = {
            @Result(column = "phone", property = "phone", typeHandler = EncryptedStringTypeHandler.class)
    })
    @Select("<script>"
            + "SELECT u.id, u.nickname, u.avatar, u.phone, u.gender, u.school_id schoolId, s.name schoolName, "
            + "       u.verify_status verifyStatus, u.status, u.mute_until muteUntil, u.cancelled_at cancelledAt, "
            + "       u.fans_count fansCount, u.follow_count followCount, u.created_at createdAt, "
            + "       IF(EXISTS(SELECT 1 FROM t_user_blacklist b WHERE b.user_id = u.id AND b.status = 1), 1, 0) blacklisted, "
            + "       IF(EXISTS(SELECT 1 FROM t_user_mute m WHERE m.user_id = u.id AND m.status = 1 "
            + "                 AND (m.end_at IS NULL OR m.end_at &gt; NOW())), 1, 0) muted "
            + "FROM t_user u "
            + "LEFT JOIN t_school s ON s.id = u.school_id "
            + "WHERE 1 = 1 "
            + "<if test='keywords != null and keywords.size() &gt; 0'>"
            // 昵称走多词 AND（括号包住），手机号盲索引是精确命中，只能整体 OR 在括号外：
            // ((昵称含甲 AND 昵称含乙) OR phone_hash = 甲) —— 不会把 AND 语义破坏掉。
            + "  AND (("
            + "    <foreach collection='keywords' item='kw' separator=' AND '>u.nickname LIKE CONCAT('%', #{kw}, '%')</foreach>"
            + "  )<if test='phoneHash != null'> OR u.phone_hash = #{phoneHash}</if>) "
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
                               @Param("keywords") List<String> keywords,
                               @Param("phoneHash") String phoneHash,
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
     *
     * <p>详情里的实名/学号给的是解密后的明文（后台审核需要核对），列表页只给手机号且已脱敏，
     * 遵循「最小必要」原则。
     */
    @Results(id = "userRowDetail", value = {
            @Result(column = "phone", property = "phone", typeHandler = EncryptedStringTypeHandler.class),
            @Result(column = "realName", property = "realName", typeHandler = EncryptedStringTypeHandler.class),
            @Result(column = "studentNo", property = "studentNo", typeHandler = EncryptedStringTypeHandler.class)
    })
    @Select("<script>"
            + "SELECT u.id, u.nickname, u.avatar, u.phone, u.gender, u.school_id schoolId, s.name schoolName, "
            + "       u.verify_status verifyStatus, u.status, u.mute_until muteUntil, u.bio, "
            + "       u.cancelled_at cancelledAt, "
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

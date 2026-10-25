package com.campus.post.mapper;

import com.campus.post.dto.AdMaterialVO;
import com.campus.post.dto.NoticeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 首页运营位查询：广告素材 + 后台公告。
 *
 * <p>两张表的主人是 campus-admin（运营在后台维护），这里纯读，
 * 与 campus-user 持有 t_report_ticket 副本是同一套「共享库 + 各自持有」的做法。
 *
 * <p><b>公共无 Cookie 的背后是一致的过滤口径</b>——必须同时满足：
 * 上架 + 审核通过 + 处于投放时间窗 + 地域定向命中。
 * 少判任何一个条件都会把还没过审的广告推给用户，这是运营事故。
 *
 * <p>这里把 slot 与 material 用 JOIN 一次查完，避免先查槽位再查素材的两次往返；
 * 槽位的 max_count 用 LIMIT 在 SQL 层生效。
 */
@Mapper
public interface OperateQueryMapper {

    /**
     * 取某个广告位当前可投放的素材。
     *
     * @param code      广告位编码
     * @param city      用户所在城市（可为 null，此时不匹配「城市定向」的广告位）
     * @param schoolId  用户学校 id（可为 null，此时不匹配「校区定向」的广告位）
     * @param limit     取多少条（传 slot.max_count）
     */
    @Select("SELECT m.id, m.title, m.image, m.link_type, m.link_value, m.sort, s.code AS slot_code "
            + "FROM t_ad_material m JOIN t_ad_slot s ON s.id = m.slot_id "
            + "WHERE s.code = #{code} "
            + "  AND s.status = 1 AND s.audit_status = 1 "
            + "  AND (s.start_at IS NULL OR s.start_at <= NOW()) "
            + "  AND (s.end_at IS NULL OR s.end_at >= NOW()) "
            + "  AND m.status = 1 AND m.audit_status = 1 "
            + "  AND (m.start_at IS NULL OR m.start_at <= NOW()) "
            + "  AND (m.end_at IS NULL OR m.end_at >= NOW()) "
            + "  AND ( s.scope_type = 1 "
            + "     OR (s.scope_type = 2 AND #{city} IS NOT NULL AND s.city = #{city}) "
            + "     OR (s.scope_type = 3 AND #{schoolId} IS NOT NULL AND s.school_id = #{schoolId}) ) "
            + "ORDER BY m.sort DESC, m.id DESC LIMIT #{limit}")
    List<AdMaterialVO> selectMaterials(@Param("code") String code,
                                       @Param("city") String city,
                                       @Param("schoolId") Long schoolId,
                                       @Param("limit") int limit);

    /** 广告位的同时在线上限；查不到（编码不存在/未过审）返回 null */
    @Select("SELECT max_count FROM t_ad_slot "
            + "WHERE code = #{code} AND status = 1 AND audit_status = 1 "
            + "  AND (start_at IS NULL OR start_at <= NOW()) "
            + "  AND (end_at IS NULL OR end_at >= NOW()) LIMIT 1")
    Integer selectSlotMaxCount(@Param("code") String code);

    @Update("UPDATE t_ad_material SET view_count = view_count + 1 WHERE id = #{id}")
    int incrView(@Param("id") Long id);

    @Update("UPDATE t_ad_material SET click_count = click_count + 1 WHERE id = #{id}")
    int incrClick(@Param("id") Long id);

    /** 素材存在性校验（挡住用不存在的 id 刷计数） */
    @Select("SELECT COUNT(*) FROM t_ad_material WHERE id = #{id}")
    int countMaterial(@Param("id") Long id);

    /**
     * 小程序可见的已发布公告。
     *
     * <p><b>target_type 是安全边界，不是可选项</b>：1 是「后台管理员」的内部通知，
     * 里面可能有审核口径、违规处置细节，推给小程序用户就是信息泄露。
     * 所以这里只放行 2（小程序全体）与 3（指定校区且命中本人的 school_id）。
     *
     * <p>未登录用户 schoolId 为 null，拿不到任何「指定校区」的公告——
     * 定向到某校的公告不该被外校的人看到。
     */
    @Select("SELECT id, title, content, type, level, publish_at "
            + "FROM t_admin_notice "
            + "WHERE status = 1 "
            + "  AND (publish_at IS NULL OR publish_at <= NOW()) "
            + "  AND (offline_at IS NULL OR offline_at >= NOW()) "
            + "  AND ( target_type = 2 "
            + "     OR (target_type = 3 AND #{schoolId} IS NOT NULL AND school_id = #{schoolId}) ) "
            + "ORDER BY level DESC, publish_at DESC, id DESC LIMIT #{limit}")
    List<NoticeVO> selectNotices(@Param("schoolId") Long schoolId,
                                 @Param("limit") int limit);

    /** 公告详情（同样受 target_type 边界约束，只看得到自己有权限的那部分） */
    @Select("SELECT id, title, content, type, level, publish_at "
            + "FROM t_admin_notice "
            + "WHERE id = #{id} AND status = 1 "
            + "  AND (publish_at IS NULL OR publish_at <= NOW()) "
            + "  AND (offline_at IS NULL OR offline_at >= NOW()) "
            + "  AND ( target_type = 2 "
            + "     OR (target_type = 3 AND #{schoolId} IS NOT NULL AND school_id = #{schoolId}) ) "
            + "LIMIT 1")
    NoticeVO selectNotice(@Param("id") Long id, @Param("schoolId") Long schoolId);
}

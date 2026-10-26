package com.campus.post.mapper;

import com.campus.post.dto.AdMaterialVO;
import com.campus.post.dto.NoticeReceipt;
import com.campus.post.dto.NoticeVO;
import org.apache.ibatis.annotations.Insert;
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
     * <p>定向匹配用 {@code FIND_IN_SET} 而不是 {@code =}：后台的 city / school_ids
     * 都是<b>逗号分隔多值</b>（一个广告位可以同时投多个城市或校区），
     * 等值比较只能命中「恰好只投这一个」的行，投给 3 所学校的广告位会一个都匹配不上。
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
            + "     OR (s.scope_type = 2 AND #{city} IS NOT NULL AND FIND_IN_SET(#{city}, s.city)) "
            + "     OR (s.scope_type = 3 AND #{schoolId} IS NOT NULL AND FIND_IN_SET(#{schoolId}, s.school_ids)) ) "
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
     * C 端可见公告的统一过滤口径，三处查询（列表 / 详情 / 弹窗）必须一字不差。
     *
     * <p><b>target_type 是安全边界，不是可选项</b>：1 是「后台管理员」的内部通知，
     * 里面可能有审核口径、违规处置细节，推给小程序用户就是信息泄露。
     * 所以这里只放行 2（小程序全体）与 3（指定校区且命中本人的 school_id）。
     *
     * <p>未登录用户 schoolId 为 null，拿不到任何「指定校区」的公告——
     * 定向到某校的公告不该被外校的人看到。
     *
     * <p>offline_at 这一条踩过坑：后台下线时写入的时间若在重新发布时不清空，
     * 公告会永远被判为「已过期」，后台显示已发布而 C 端一片空白。
     */
    String VISIBLE_WHERE = "status = 1 "
            + "  AND (publish_at IS NULL OR publish_at <= NOW()) "
            + "  AND (offline_at IS NULL OR offline_at >= NOW()) "
            + "  AND ( target_type = 2 "
            + "     OR (target_type = 3 AND #{schoolId} IS NOT NULL AND school_id = #{schoolId}) ) ";

    /** 小程序可见的已发布公告 */
    @Select("SELECT id, title, content, type, level, popup_mode, publish_at "
            + "FROM t_admin_notice WHERE " + VISIBLE_WHERE
            + "ORDER BY level DESC, publish_at DESC, id DESC LIMIT #{limit}")
    List<NoticeVO> selectNotices(@Param("schoolId") Long schoolId,
                                 @Param("limit") int limit);

    /** 公告详情（同样受 target_type 边界约束，只看得到自己有权限的那部分） */
    @Select("SELECT id, title, content, type, level, popup_mode, publish_at "
            + "FROM t_admin_notice WHERE id = #{id} AND " + VISIBLE_WHERE
            + "LIMIT 1")
    NoticeVO selectNotice(@Param("id") Long id, @Param("schoolId") Long schoolId);

    /**
     * 当前可能该弹出的公告（按优先级取前几条，由 Service 逐条判频次）。
     *
     * <p>为什么不是「只取一条」：排第一的那条可能已经按「确认收到」静默过了，
     * 如果只取一条，后面那条运营新发的公告就永远轮不到弹出。
     * 取 5 条足够——同屏弹窗本来就是极低频能力，没人会同时挂几十条。
     *
     * <p>排序沿用公告列表口径（级别高的优先、同级按发布时间新→旧），
     * 所以运营想让某条先弹，把它设成「重要/紧急」即可。
     */
    @Select("SELECT id, title, content, type, level, popup_mode, publish_at "
            + "FROM t_admin_notice WHERE popup_mode > 0 AND " + VISIBLE_WHERE
            + "ORDER BY level DESC, publish_at DESC, id DESC LIMIT 5")
    List<NoticeVO> selectPopupNotices(@Param("schoolId") Long schoolId);

    /** 某个用户对某条公告的回执；没弹过/没确认过返回 null */
    @Select("SELECT notice_id, first_shown_at, last_shown_at, confirmed_at "
            + "FROM t_notice_receipt WHERE user_id = #{userId} AND notice_id = #{noticeId} LIMIT 1")
    NoticeReceipt selectReceipt(@Param("userId") Long userId, @Param("noticeId") Long noticeId);

    /**
     * 记一次弹出。
     *
     * <p>用唯一键 uk_user_notice 做 upsert，避免并发下同一个用户出现两行回执
     * （那会让「每天一次」判不准）。
     */
    @Insert("INSERT INTO t_notice_receipt (id, user_id, notice_id, shown_count, first_shown_at, last_shown_at) "
            + "VALUES (#{id}, #{userId}, #{noticeId}, 1, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE shown_count = shown_count + 1, last_shown_at = NOW()")
    int upsertShown(@Param("id") Long id, @Param("userId") Long userId, @Param("noticeId") Long noticeId);

    /** 用户点「我知道了」：写入确认时间，之后 popup_mode=3 的公告不再打扰他 */
    @Insert("INSERT INTO t_notice_receipt (id, user_id, notice_id, shown_count, first_shown_at, last_shown_at, confirmed_at) "
            + "VALUES (#{id}, #{userId}, #{noticeId}, 1, NOW(), NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE confirmed_at = IFNULL(confirmed_at, NOW())")
    int upsertConfirm(@Param("id") Long id, @Param("userId") Long userId, @Param("noticeId") Long noticeId);

    /** 确认回执前先验公告确实存在，挡住用任意 id 刷记录 */
    @Select("SELECT COUNT(*) FROM t_admin_notice WHERE id = #{id}")
    int countNotice(@Param("id") Long id);
}

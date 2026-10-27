package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.dto.GroupRow;
import com.campus.message.entity.Group;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMapper extends BaseMapper<Group> {

    /**
     * 按 (type, bizKey) 取群。{@code uk_type_key} 唯一键保证最多一条，
     * 加 limit 1 只是防御历史脏数据。
     */
    @Select("select * from t_group where type = #{type} and biz_key = #{bizKey} limit 1")
    Group selectByTypeAndKey(@Param("type") Integer type, @Param("bizKey") String bizKey);

    /**
     * 成员数增减。用 greatest 兜底成非负：并发退群时 member_count 绝不能变成 -1，
     * 否则「按群规模自适应频率」会算出一个荒谬的值。
     */
    @Update("update t_group set member_count = greatest(member_count + #{delta}, 0) where id = #{id}")
    int incrMemberCount(@Param("id") Long id, @Param("delta") int delta);

    /** 建群时取校名（各服务本地共库，与 MessageMapper 直接聚合 t_like 的做法一致） */
    @Select("select name from t_school where id = #{schoolId} limit 1")
    String selectSchoolName(@Param("schoolId") Long schoolId);

    /** 有新消息时刷新群摘要（列表页展示用，避免每次子查询最后一条） */
    @Update("update t_group set last_msg_id = #{msgId}, last_msg_text = #{text}, "
            + "last_msg_time = #{time} where id = #{id} and last_msg_id < #{msgId}")
    int updateLastMessage(@Param("id") Long id, @Param("msgId") Long msgId,
                          @Param("text") String text, @Param("time") LocalDateTime time);

    /**
     * 强制覆盖群摘要。
     *
     * <p>撤回最后一条消息时必须用它：{@link #updateLastMessage} 带
     * {@code last_msg_id < msgId} 的单调保护（防乱序回退），
     * 而撤回恰恰要「往回退」，用那条 SQL 会一行都更新不到，
     * 结果群列表一直显示已经撤回的内容。
     */
    @Update("update t_group set last_msg_id = #{msgId}, last_msg_text = #{text}, "
            + "last_msg_time = #{time} where id = #{id}")
    int forceLastMessage(@Param("id") Long id, @Param("msgId") Long msgId,
                         @Param("text") String text, @Param("time") LocalDateTime time);

    /**
     * 刷新「最近一次 @所有人」游标。<b>这是零写放大的关键</b>：
     * 一万人的群发一次 @全体，这里只更新 1 行，而不是给一万个成员各 +1。
     */
    @Update("update t_group set last_at_all_msg_id = #{msgId} "
            + "where id = #{id} and last_at_all_msg_id < #{msgId}")
    int updateLastAtAll(@Param("id") Long id, @Param("msgId") Long msgId);

    /**
     * 批量取用户摘要（昵称/头像），用于把历史消息的昵称快照刷新成最新。
     *
     * <p>放在 GroupMapper 里是刻意的：本项目各服务本地共库，
     * {@code MessageMapper} 也直接聚合 t_like / t_goods，
     * 一页 50 条消息用一次 IN 查询补齐，比逐条走 Feign 便宜得多。
     */
    @Select("<script>"
            + "select id, nickname, avatar from t_user where id in "
            + "<foreach collection='ids' item='it' open='(' separator=',' close=')'>#{it}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectUserBriefs(@Param("ids") List<Long> ids);

    /** 后台群列表（带校名与今日消息数） */
    @Select("<script>"
            + "select g.id, g.name, s.name as school_name, g.member_count, "
            + " (select count(*) from t_group_message m "
            + "   where m.group_id = g.id and m.created_at &gt;= curdate()) as today_count, "
            + " date_format(g.last_msg_time, '%Y-%m-%d %H:%i') as last_msg_time, "
            + " g.status, g.night_enabled, "
            + " concat(g.night_start, '-', g.night_end) as night_range, "
            + " g.rate_gap_sec, g.rate_day_max, "
            + " date_format(g.created_at, '%Y-%m-%d %H:%i') as created_at "
            + "from t_group g left join t_school s on s.id = g.school_id "
            + "where 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  and (g.name like concat('%', #{keyword}, '%') or s.name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "<if test=\"status != null\"> and g.status = #{status} </if>"
            + "order by g.member_count desc, g.id desc "
            + "limit #{offset}, #{size}"
            + "</script>")
    List<GroupRow> pageGroups(@Param("keyword") String keyword, @Param("status") Integer status,
                              @Param("offset") long offset, @Param("size") long size);

    @Select("<script>"
            + "select count(*) from t_group g left join t_school s on s.id = g.school_id "
            + "where 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  and (g.name like concat('%', #{keyword}, '%') or s.name like concat('%', #{keyword}, '%')) "
            + "</if>"
            + "<if test=\"status != null\"> and g.status = #{status} </if>"
            + "</script>")
    long countGroups(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("select coalesce(sum(member_count), 0) from t_group")
    long sumMemberCount();

    @Select("select count(*) from t_group")
    long countAll();

    @Select("select count(*) from t_group_message where created_at >= curdate()")
    long countTodayMessages();

    @Select("select count(distinct group_id) from t_group_message where created_at >= date_sub(now(), interval 24 hour)")
    long countActiveGroups();

    /** 后台消息列表（支持按内容搜索） */
    @Select("<script>"
            + "select m.* from t_group_message m where m.group_id = #{groupId} "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  and m.content like concat('%', #{keyword}, '%') "
            + "</if>"
            + "order by m.id desc limit #{offset}, #{size}"
            + "</script>")
    List<com.campus.message.entity.GroupMessage> pageMessagesAdmin(@Param("groupId") Long groupId,
                                                                   @Param("keyword") String keyword,
                                                                   @Param("offset") long offset,
                                                                   @Param("size") long size);

    @Select("<script>"
            + "select count(*) from t_group_message m where m.group_id = #{groupId} "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  and m.content like concat('%', #{keyword}, '%') "
            + "</if>"
            + "</script>")
    long countMessagesAdmin(@Param("groupId") Long groupId, @Param("keyword") String keyword);
}

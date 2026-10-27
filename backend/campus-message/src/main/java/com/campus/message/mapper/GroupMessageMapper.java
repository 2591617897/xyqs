package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.GroupMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GroupMessageMapper extends BaseMapper<GroupMessage> {

    /** 首次进群：最近一页（倒序取完在 Service 里反转成正序） */
    @Select("select * from t_group_message where group_id = #{groupId} and deleted = 0 "
            + "order by id desc limit #{limit}")
    List<GroupMessage> selectRecent(@Param("groupId") Long groupId, @Param("limit") int limit);

    /**
     * 增量补拉：{@code id > sinceId} 正序。
     *
     * <p>这是 WebSocket 断线重连后「不漏消息」的唯一保证，
     * 也是 HTTP 轮询兜底走的同一条 SQL。
     */
    @Select("select * from t_group_message where group_id = #{groupId} and id > #{sinceId} "
            + "and deleted = 0 order by id asc limit #{limit}")
    List<GroupMessage> selectSince(@Param("groupId") Long groupId, @Param("sinceId") Long sinceId,
                                   @Param("limit") int limit);

    /** 上滑翻历史：比 beforeId 更早的一批（倒序） */
    @Select("select * from t_group_message where group_id = #{groupId} and id < #{beforeId} "
            + "and deleted = 0 order by id desc limit #{limit}")
    List<GroupMessage> selectBefore(@Param("groupId") Long groupId, @Param("beforeId") Long beforeId,
                                    @Param("limit") int limit);

    /**
     * 群未读：最多扫 limit 行。
     * 返回条数 = limit 说明「没数完」，前端显示 99+，不必真的数清一万条。
     */
    @Select("select id from t_group_message where group_id = #{groupId} and id > #{sinceId} "
            + "and sender_id != #{meId} and deleted = 0 order by id asc limit #{limit}")
    List<Long> selectUnreadIds(@Param("groupId") Long groupId, @Param("sinceId") Long sinceId,
                               @Param("meId") Long meId, @Param("limit") int limit);

    /** @所有人 未读条数：一天通常 0~2 次，COUNT 开销可忽略 */
    @Select("select count(*) from t_group_message where group_id = #{groupId} "
            + "and at_type = 2 and id > #{sinceId} and deleted = 0")
    int countAtAllUnread(@Param("groupId") Long groupId, @Param("sinceId") Long sinceId);

    /** 归档取数：早于 before 的一批，按创建时间正序（走 idx_created） */
    @Select("select * from t_group_message where created_at < #{before} order by created_at asc limit #{limit}")
    List<GroupMessage> selectOlderThan(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /** 归档后从热表删除（分批，绝不做 INSERT...SELECT...DELETE 一条大 SQL） */
    @Update("<script>"
            + "delete from t_group_message where id in "
            + "<foreach collection='ids' item='it' open='(' separator=',' close=')'>#{it}</foreach>"
            + "</script>")
    int deleteByIds(@Param("ids") List<Long> ids);

    /** 后台删消息：软删，与用户撤回写同一列 */
    @Update("update t_group_message set deleted = 1 where id = #{id} and group_id = #{groupId} and deleted = 0")
    int softDelete(@Param("id") Long id, @Param("groupId") Long groupId);
}

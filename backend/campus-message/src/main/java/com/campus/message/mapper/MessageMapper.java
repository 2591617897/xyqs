package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /** 会话内最近的消息，升序返回（limit 条）；已撤回（deleted=1）的不返回 */
    @Select("select * from ("
            + "select * from t_message where conversation_id = #{conversationId} and deleted = 0 "
            + "order by id desc limit #{limit}"
            + ") t order by t.id asc")
    List<Message> selectRecent(@Param("conversationId") Long conversationId, @Param("limit") long limit);

    /**
     * 撤回一条消息：仅发送者本人可删，且只能删未撤回过的。
     * 返回受影响行数，0 表示「不存在 / 不是我发的 / 已撤回过」。
     */
    @Update("update t_message set deleted = 1 "
            + "where id = #{id} and conversation_id = #{conversationId} "
            + "and sender_id = #{senderId} and deleted = 0")
    int softDelete(@Param("id") Long id,
                   @Param("conversationId") Long conversationId,
                   @Param("senderId") Long senderId);

    /** 会话内最后一条未撤回的消息（撤回后用来重算会话摘要） */
    @Select("select * from t_message "
            + "where conversation_id = #{conversationId} and deleted = 0 "
            + "order by id desc limit 1")
    Message selectLastAlive(@Param("conversationId") Long conversationId);

    /**
     * 批量查消息发送方：用于会话列表标记「最后一条是我发的还是对方发的」。
     * 返回 id -> sender_id 的映射。
     */
    @Select("<script>"
            + "select id, sender_id from t_message where id in "
            + "<foreach collection='ids' item='it' open='(' separator=',' close=')'>#{it}</foreach>"
            + "</script>")
    List<Message> selectSenders(@Param("ids") List<Long> ids);

    /** 把对方发给我的消息标为已读 */
    @Update("update t_message set read_flag = 1 "
            + "where conversation_id = #{conversationId} and receiver_id = #{receiverId} and read_flag = 0")
    int markRead(@Param("conversationId") Long conversationId, @Param("receiverId") Long receiverId);

    /**
     * 消息中心角标统计。
     * 说明：各服务本地共库，这里做只读聚合算「我收到的赞 / 评论」。
     * 生产若按服务拆库，应改为各服务提供计数接口或落通知表。
     */
    @Select("select coalesce(sum(c), 0) from ("
            + "  select count(1) c from t_like l join t_goods g on g.id = l.target_id "
            + "    where l.target_type = 'GOODS' and g.user_id = #{userId}"
            + "  union all"
            + "  select count(1) from t_like l join t_wall_post w on w.id = l.target_id "
            + "    where l.target_type = 'WALL' and w.user_id = #{userId}"
            + "  union all"
            + "  select count(1) from t_like l join t_post p on p.id = l.target_id "
            + "    where l.target_type = 'POST' and p.user_id = #{userId}"
            + ") t")
    int countReceivedLikes(@Param("userId") Long userId);

    @Select("select coalesce(sum(c), 0) from ("
            + "  select count(1) c from t_wall_comment c join t_wall_post w on w.id = c.post_id "
            + "    where w.user_id = #{userId} and c.user_id <> #{userId}"
            + "  union all"
            + "  select count(1) from t_post_comment c join t_post p on p.id = c.post_id "
            + "    where p.user_id = #{userId} and c.user_id <> #{userId}"
            + ") t")
    int countReceivedComments(@Param("userId") Long userId);
}

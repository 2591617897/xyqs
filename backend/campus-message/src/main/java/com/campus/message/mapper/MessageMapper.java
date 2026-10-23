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

    /** 会话内最近的消息，升序返回（limit 条） */
    @Select("select * from ("
            + "select * from t_message where conversation_id = #{conversationId} "
            + "order by id desc limit #{limit}"
            + ") t order by t.id asc")
    List<Message> selectRecent(@Param("conversationId") Long conversationId, @Param("limit") long limit);

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

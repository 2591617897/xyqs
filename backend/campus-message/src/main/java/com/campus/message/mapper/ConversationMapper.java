package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /** 按 (userA, userB, goodsId) 定位会话，用于「有则复用、无则创建」 */
    @Select("select * from t_message_conversation "
            + "where user_a = #{userA} and user_b = #{userB} and goods_id = #{goodsId} limit 1")
    Conversation selectByPair(@Param("userA") Long userA,
                              @Param("userB") Long userB,
                              @Param("goodsId") Long goodsId);

    /** 我的会话列表，按最后一条消息时间倒序 */
    @Select("select * from t_message_conversation "
            + "where status = 1 and (user_a = #{userId} or user_b = #{userId}) "
            + "order by (case when last_time is null then created_at else last_time end) desc "
            + "limit #{limit}")
    List<Conversation> selectMine(@Param("userId") Long userId, @Param("limit") long limit);

    /** 我的未读消息总数 */
    @Select("select coalesce(sum(case when user_a = #{userId} then unread_a else unread_b end), 0) "
            + "from t_message_conversation "
            + "where status = 1 and (user_a = #{userId} or user_b = #{userId})")
    int countUnread(@Param("userId") Long userId);

    /** 收到新消息：更新会话摘要 + 给接收方累加未读 */
    @Update("update t_message_conversation set last_msg_id = #{lastMsgId}, last_content = #{lastContent}, "
            + "last_time = #{lastTime}, unread_a = unread_a + #{deltaA}, unread_b = unread_b + #{deltaB} "
            + "where id = #{id}")
    int refreshAndIncrUnread(@Param("id") Long id,
                             @Param("lastMsgId") Long lastMsgId,
                             @Param("lastContent") String lastContent,
                             @Param("lastTime") LocalDateTime lastTime,
                             @Param("deltaA") int deltaA,
                             @Param("deltaB") int deltaB);

    @Update("update t_message_conversation set unread_a = 0 where id = #{id}")
    int resetUnreadA(@Param("id") Long id);

    @Update("update t_message_conversation set unread_b = 0 where id = #{id}")
    int resetUnreadB(@Param("id") Long id);
}

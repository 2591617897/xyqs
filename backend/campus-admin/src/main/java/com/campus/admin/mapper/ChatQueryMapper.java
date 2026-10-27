package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.admin.dto.ConversationRow;
import com.campus.admin.dto.MessageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 私信跨表只读查询。
 *
 * <p>聊天记录是本项目最敏感的数据，能查到这里的账号本身就极少数，
 * 因此查询动作由 ChatService 额外写一条操作日志（而非仅依赖网关留痕）。
 */
@Mapper
public interface ChatQueryMapper {

    @Select("<script>"
            + "SELECT c.id, c.user_a userA, c.user_b userB, ua.nickname userNameA, ub.nickname userNameB, "
            + "       c.goods_id goodsId, c.goods_title goodsTitle, c.anon, "
            + "       c.last_content lastContent, c.last_time lastTime, c.status, "
            + "       (SELECT COUNT(*) FROM t_message m WHERE m.conversation_id = c.id AND m.deleted = 0) msgCount "
            + "FROM t_message_conversation c "
            + "LEFT JOIN t_user ua ON ua.id = c.user_a "
            + "LEFT JOIN t_user ub ON ub.id = c.user_b "
            + "WHERE 1 = 1 "
            + "<if test='keywords != null and keywords.size() &gt; 0'>"
            + "  <foreach collection='keywords' item='kw'>"
            + "    AND (ua.nickname LIKE CONCAT('%', #{kw}, '%') OR ub.nickname LIKE CONCAT('%', #{kw}, '%')) "
            + "  </foreach>"
            + "</if>"
            + "<if test='userId != null'>AND (c.user_a = #{userId} OR c.user_b = #{userId}) </if>"
            + "ORDER BY c.last_time DESC, c.id DESC"
            + "</script>")
    IPage<ConversationRow> selectConversations(IPage<ConversationRow> page,
                                               @Param("keywords") List<String> keywords,
                                               @Param("userId") Long userId);

    @Select("SELECT m.id, m.conversation_id conversationId, m.sender_id senderId, su.nickname senderName, "
            + "       m.receiver_id receiverId, ru.nickname receiverName, m.type, m.content, "
            + "       m.read_flag readFlag, m.deleted, m.created_at createdAt "
            + "FROM t_message m "
            + "LEFT JOIN t_user su ON su.id = m.sender_id "
            + "LEFT JOIN t_user ru ON ru.id = m.receiver_id "
            + "WHERE m.id = #{id}")
    MessageRow selectMessage(@Param("id") Long id);

    @Select("<script>"
            + "SELECT m.id, m.conversation_id conversationId, m.sender_id senderId, su.nickname senderName, "
            + "       m.receiver_id receiverId, ru.nickname receiverName, m.type, m.content, "
            + "       m.read_flag readFlag, m.deleted, m.created_at createdAt "
            + "FROM t_message m "
            + "LEFT JOIN t_user su ON su.id = m.sender_id "
            + "LEFT JOIN t_user ru ON ru.id = m.receiver_id "
            + "WHERE 1 = 1 "
            + "<if test='conversationId != null'>AND m.conversation_id = #{conversationId} </if>"
            + "<if test='senderId != null'>AND m.sender_id = #{senderId} </if>"
            + "<if test='keywords != null and keywords.size() &gt; 0'>"
            + "  <foreach collection='keywords' item='kw'>AND m.content LIKE CONCAT('%', #{kw}, '%') </foreach>"
            + "</if>"
            + "ORDER BY m.id DESC"
            + "</script>")
    IPage<MessageRow> selectMessages(IPage<MessageRow> page,
                                     @Param("conversationId") Long conversationId,
                                     @Param("senderId") Long senderId,
                                     @Param("keywords") List<String> keywords);
}

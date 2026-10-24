package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.ConversationHide;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationHideMapper extends BaseMapper<ConversationHide> {

    /** 是否已隐藏 */
    @Select("select count(1) from t_message_conversation_hide "
            + "where user_id = #{userId} and conversation_id = #{conversationId}")
    int countHide(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    /**
     * 取消隐藏。
     * 对方发来新消息时调用——会话重新出现在列表里（与主流 IM 一致）。
     */
    @Delete("delete from t_message_conversation_hide "
            + "where user_id = #{userId} and conversation_id = #{conversationId}")
    int deleteHide(@Param("userId") Long userId, @Param("conversationId") Long conversationId);
}

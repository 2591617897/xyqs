package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.GroupMention;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GroupMentionMapper extends BaseMapper<GroupMention> {

    /**
     * 批量写 @ 提醒。用 INSERT IGNORE：同一 (messageId,userId) 重复写（重发/重试）
     * 直接忽略，不会因为唯一键冲突把整条消息的发送打回。
     */
    @Insert("<script>"
            + "insert ignore into t_group_mention (id, group_id, message_id, user_id, read_flag, created_at) values "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.id}, #{it.groupId}, #{it.messageId}, #{it.userId}, 0, now())"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<GroupMention> list);

    /** 进群/上报已读时，把这个群里 @我 的记录一次标已读 */
    @Update("update t_group_mention set read_flag = 1 "
            + "where group_id = #{groupId} and user_id = #{userId} and read_flag = 0")
    int markReadByGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("select count(*) from t_group_mention where group_id = #{groupId} "
            + "and user_id = #{userId} and read_flag = 0")
    int countUnread(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 归档时清理已迁走的 @ 记录。
     *
     * <p>这里的选择与方案 v2.0 略有出入（方案说「迁移」）：12 个月前的 @ 提醒
     * 用户早已读过、也没有任何入口能翻到，单独建第 7 张归档表只增加复杂度，
     * 因此直接删除。判断依据保留在归档消息自身的 {@code at_user_ids} 里。
     */
    @Update("<script>"
            + "delete from t_group_mention where message_id in "
            + "<foreach collection='ids' item='it' open='(' separator=',' close=')'>#{it}</foreach>"
            + "</script>")
    int deleteByMessageIds(@Param("ids") List<Long> ids);

    /** 未读 @ 我 的消息 id（点角标跳到具体消息用） */
    @Select("select message_id from t_group_mention where group_id = #{groupId} and user_id = #{userId} "
            + "and read_flag = 0 order by message_id desc limit #{limit}")
    List<Long> selectUnreadMessageIds(@Param("groupId") Long groupId, @Param("userId") Long userId,
                                      @Param("limit") int limit);
}

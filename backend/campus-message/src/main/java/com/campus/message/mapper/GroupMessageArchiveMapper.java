package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.GroupMessageArchive;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupMessageArchiveMapper extends BaseMapper<GroupMessageArchive> {

    /**
     * 写入归档表。用 INSERT IGNORE：归档任务在「已插入、未删除」之间崩掉后重跑，
     * 不会因为主键已存在而报错中断（分两步写就是为了这个可重试性）。
     */
    @Insert("<script>"
            + "insert ignore into t_group_message_archive "
            + "(id, group_id, sender_id, sender_name, sender_avatar, type, content, "
            + " at_type, at_user_ids, deleted, created_at, archived_at) values "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.id}, #{it.groupId}, #{it.senderId}, #{it.senderName}, #{it.senderAvatar}, "
            + " #{it.type}, #{it.content}, #{it.atType}, #{it.atUserIds}, #{it.deleted}, "
            + " #{it.createdAt}, now())"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<GroupMessageArchive> list);
}

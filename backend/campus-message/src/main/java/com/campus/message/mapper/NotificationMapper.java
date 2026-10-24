package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 某用户某类型未读数（type 不可空） */
    @Select("SELECT COUNT(*) FROM t_notification WHERE receiver_id = #{uid} AND is_read = 0 AND type = #{type}")
    int countUnreadByType(@Param("uid") Long uid, @Param("type") String type);

    /** 某用户未读数合计（前端 tab 顶部小红点） */
    @Select("SELECT COUNT(*) FROM t_notification WHERE receiver_id = #{uid} AND is_read = 0")
    int countAllUnread(@Param("uid") Long uid);

    @Update("UPDATE t_notification SET is_read = 1 WHERE receiver_id = #{uid} AND type = #{type} AND is_read = 0")
    int markReadByType(@Param("uid") Long uid, @Param("type") String type);

    @Update("UPDATE t_notification SET is_read = 1 WHERE receiver_id = #{uid} AND is_read = 0")
    int markAllRead(@Param("uid") Long uid);
}

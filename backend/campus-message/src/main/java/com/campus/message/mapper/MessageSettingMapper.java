package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.entity.MessageSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageSettingMapper extends BaseMapper<MessageSetting> {

    /**
     * 只更新订阅授权状态。
     *
     * <p>单独一条 SQL 的原因：用户点「允许」时前端只拿到订阅结果，
     * 不该把整个设置对象回传一遍 —— 那样并发改开关会互相覆盖。
     */
    @Update("UPDATE t_message_setting SET subscribe_status = #{status}, subscribe_at = NOW() WHERE user_id = #{uid}")
    int updateSubscribe(@Param("uid") Long uid, @Param("status") int status);
}

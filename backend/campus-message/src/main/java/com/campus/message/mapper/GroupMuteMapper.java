package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.dto.GroupMuteVO;
import com.campus.message.entity.GroupMute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GroupMuteMapper extends BaseMapper<GroupMute> {

    /**
     * 当前生效的禁言记录。{@code end_at IS NULL} 是永久禁言，
     * 所以条件必须是「为空 或 未到期」，不能只写 {@code end_at > now()}。
     */
    @Select("select * from t_group_mute where group_id = #{groupId} and user_id = #{userId} "
            + "and status = 1 and (end_at is null or end_at > #{now}) "
            + "order by id desc limit 1")
    GroupMute selectActive(@Param("groupId") Long groupId, @Param("userId") Long userId,
                           @Param("now") LocalDateTime now);

    /** 同一个人重复禁言时，先把旧的生效记录收掉，避免多条 status=1 并存 */
    @Update("update t_group_mute set status = 0 "
            + "where group_id = #{groupId} and user_id = #{userId} and status = 1")
    int closeActive(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Update("update t_group_mute set status = 0 where id = #{id} and status = 1")
    int release(@Param("id") Long id);

    /** 后台禁言记录列表（带昵称）；时间在 SQL 里格式化好，避免 iOS 解析 ISO 串失败 */
    @Select("<script>"
            + "select m.id, m.group_id, m.user_id, u.nickname, u.avatar, "
            + " date_format(m.start_at, '%Y-%m-%d %H:%i') as start_at, "
            + " date_format(m.end_at, '%Y-%m-%d %H:%i') as end_at, "
            + " m.reason, m.operator_id, m.status "
            + "from t_group_mute m left join t_user u on u.id = m.user_id "
            + "where m.group_id = #{groupId} "
            + "<if test=\"activeOnly\"> and m.status = 1 and (m.end_at is null or m.end_at &gt; now()) </if>"
            + "order by m.id desc limit #{offset}, #{size}"
            + "</script>")
    List<GroupMuteVO> pageMutes(@Param("groupId") Long groupId, @Param("activeOnly") boolean activeOnly,
                                @Param("offset") long offset, @Param("size") long size);

    @Select("select count(*) from t_group_mute where group_id = #{groupId}")
    long countMutes(@Param("groupId") Long groupId);
}

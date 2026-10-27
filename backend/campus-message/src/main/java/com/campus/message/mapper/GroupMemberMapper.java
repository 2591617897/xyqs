package com.campus.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.message.dto.GroupMemberVO;
import com.campus.message.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    @Select("select * from t_group_member where group_id = #{groupId} and user_id = #{userId} limit 1")
    GroupMember selectOne(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 我在某个群里「在群」状态的成员记录（status=1 才算，被踢/退群的当未加入处理） */
    @Select("select * from t_group_member where group_id = #{groupId} and user_id = #{userId} and status = 1 limit 1")
    GroupMember selectActive(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** @我 未读数 +1：只对被 @ 的少数人写，绝不批量刷全群 */
    @Update("update t_group_member set mention_unread = mention_unread + 1 "
            + "where group_id = #{groupId} and user_id = #{userId}")
    int incrMention(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 上报已读游标：只前进不后退（用 greatest），同时清掉 @我 的未读数。
     *
     * <p>为什么用 greatest：多端同时看群（手机 + 平板），后到的旧游标不能把
     * 已经读到的地方倒退回去，否则未读数会「读完了又冒出来」。
     */
    @Update("update t_group_member set last_read_id = greatest(last_read_id, #{lastReadId}), "
            + "mention_unread = 0 where group_id = #{groupId} and user_id = #{userId}")
    int advanceRead(@Param("groupId") Long groupId, @Param("userId") Long userId,
                    @Param("lastReadId") Long lastReadId);

    /** 重新加入群（主动退群后点「重新加入」） */
    @Update("update t_group_member set status = 1, leave_at = null, join_at = now() "
            + "where group_id = #{groupId} and user_id = #{userId} and status <> 1")
    int restore(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 退群 / 被踢：只改状态，保留记录 —— 有记录才不会再被自动入群拉回来 */
    @Update("update t_group_member set status = #{status}, leave_at = now() "
            + "where group_id = #{groupId} and user_id = #{userId} and status = 1")
    int leave(@Param("groupId") Long groupId, @Param("userId") Long userId,
              @Param("status") Integer status);

    /** 设置/取消群管理员。上限校验在 Service 里做（最多 5 个） */
    @Update("update t_group_member set role = #{role} where group_id = #{groupId} and user_id = #{userId}")
    int updateRole(@Param("groupId") Long groupId, @Param("userId") Long userId,
                   @Param("role") Integer role);

    @Select("select count(*) from t_group_member where group_id = #{groupId} and role = 1 and status = 1")
    int countAdmins(@Param("groupId") Long groupId);

    /**
     * 成员列表（@ 选择器 / 群设置 / 后台共用）。
     *
     * <p>直接 join t_user 取昵称头像：各服务本地共库，比走 Feign 批量补资料少一跳，
     * 一万人群里点开 @ 选择器也不会因为 user 服务抖动而空列表。
     *
     * <p>显式列出字段而不是 {@code m.*} 的原因：{@code join_at} 是 DATETIME，
     * 而 VO 里的 joinAt 是已格式化的字符串，需要用 DATE_FORMAT 在 SQL 里转好
     * （iOS 的 new Date() 解析带 T 的 ISO 串会得到 Invalid Date）。
     */
    @Select("<script>"
            + "select m.id, m.group_id, m.user_id, u.nickname, u.avatar, u.verify_status, "
            + " m.role, m.status, m.last_read_id, "
            + " date_format(m.join_at, '%Y-%m-%d %H:%i') as join_at "
            + "from t_group_member m left join t_user u on u.id = m.user_id "
            + "where m.group_id = #{groupId} and m.status = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  and (u.nickname like concat('%', #{keyword}, '%') or m.user_id = #{keyword}) "
            + "</if>"
            + "order by m.role desc, m.join_at asc "
            + "limit #{offset}, #{size}"
            + "</script>")
    List<GroupMemberVO> pageMembers(@Param("groupId") Long groupId, @Param("keyword") String keyword,
                                    @Param("offset") long offset, @Param("size") long size);

    @Select("<script>"
            + "select count(*) from t_group_member m left join t_user u on u.id = m.user_id "
            + "where m.group_id = #{groupId} and m.status = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  and (u.nickname like concat('%', #{keyword}, '%') or m.user_id = #{keyword}) "
            + "</if>"
            + "</script>")
    long countMembers(@Param("groupId") Long groupId, @Param("keyword") String keyword);
}

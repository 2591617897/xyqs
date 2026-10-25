package com.campus.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 跨服务写失败时的降级通道：admin 同库直写业务表。
 *
 * <p>只在 Feign 调不通（服务下线 / 网络抖动）时启用，正常链路永远走各服务的
 * /internal/admin/**。同一套库让降级成为可能——如果将来按服务拆库，
 * 这里的 SQL 会失效，届时必须把降级策略改成「记录待办 + 告警」。
 *
 * <p>刻意只提供「改状态」这类幂等 SQL，不提供任何插入/删除，
 * 降级写错了也只是状态错了，能被下一次处置覆盖回来。
 */
@Mapper
public interface FallbackWriteMapper {

    @Update("UPDATE t_post SET status = #{status} WHERE id = #{id}")
    int updatePostStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE t_wall_post SET status = #{status} WHERE id = #{id}")
    int updateWallStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 后台只下架/恢复在售，绝不碰 status=2（已售出） */
    @Update("UPDATE t_goods SET status = #{status} WHERE id = #{id} AND status <> 2")
    int updateGoodsStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE t_user SET status = #{status} WHERE id = #{id}")
    int updateUserStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE t_user SET mute_until = #{until} WHERE id = #{id}")
    int updateUserMuteUntil(@Param("id") Long id, @Param("until") LocalDateTime until);

    @Update("UPDATE t_user SET verify_status = #{vs}, verify_remark = #{remark} WHERE id = #{id}")
    int updateUserVerify(@Param("id") Long id, @Param("vs") Integer vs, @Param("remark") String remark);

    @Update("UPDATE t_message SET deleted = 1 WHERE id = #{id}")
    int deleteMessage(@Param("id") Long id);

    // 评论没有状态列，删除即物理删除，降级路径与原路径结果一致
    @Update("DELETE FROM t_post_comment WHERE id = #{id}")
    int deletePostComment(@Param("id") Long id);

    @Update("DELETE FROM t_wall_comment WHERE id = #{id}")
    int deleteWallComment(@Param("id") Long id);

    @Update("DELETE FROM t_goods_comment WHERE id = #{id}")
    int deleteGoodsComment(@Param("id") Long id);

    /** 降级删评论时同步回写计数，保证降级与正常路径的最终结果一致 */
    @Update("UPDATE t_post SET comment_count = greatest(comment_count - 1, 0) WHERE id = #{id}")
    int decPostCommentCount(@Param("id") Long id);

    @Update("UPDATE t_wall_post SET comment_count = greatest(comment_count - 1, 0) WHERE id = #{id}")
    int decWallCommentCount(@Param("id") Long id);

    @Update("UPDATE t_goods SET comment_count = greatest(comment_count - 1, 0) WHERE id = #{id}")
    int decGoodsCommentCount(@Param("id") Long id);
}

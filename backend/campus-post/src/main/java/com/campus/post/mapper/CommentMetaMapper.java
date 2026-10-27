package com.campus.post.mapper;

import com.campus.post.dto.CommentUserBrief;
import com.campus.post.dto.TargetCountBrief;
import com.campus.post.dto.UserCountBrief;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 评论区的批量元数据查询（用户资料 / 等级活跃度 / 评论点赞数）。
 *
 * <p>共享库直查 t_user / t_school / t_like，与 {@link UserSnapshotMapper} 读 t_user、
 * {@link SchoolRegionMapper} 读 t_school 是同一套做法：同库跨表只读，不走 Feign。
 *
 * <p>全部走「一次 IN 查询 + 内存合并」，杜绝评论列表的 N+1。
 */
@Mapper
public interface CommentMetaMapper {

    /**
     * 评论者资料：昵称 / 头像 / 学校所在省市。
     * LEFT JOIN t_school：没绑学校的用户也要能查到行（省市为空）。
     */
    @Select("<script>"
            + "SELECT u.id, u.nickname, u.avatar, u.school_id, s.province, s.city "
            + "FROM t_user u LEFT JOIN t_school s ON s.id = u.school_id "
            + "WHERE u.id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<CommentUserBrief> selectUsers(@Param("ids") Collection<Long> ids);

    /** 发帖数（t_post 有 idx_user(user_id, status) 索引，走索引） */
    @Select("<script>"
            + "SELECT user_id, COUNT(*) AS cnt FROM t_post WHERE user_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY user_id"
            + "</script>")
    List<UserCountBrief> selectPostCounts(@Param("ids") Collection<Long> ids);

    /** 评论数（含楼中楼回复）。t_post_comment 需有 idx_user(user_id) 索引，见迁移脚本 */
    @Select("<script>"
            + "SELECT user_id, COUNT(*) AS cnt FROM t_post_comment WHERE user_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY user_id"
            + "</script>")
    List<UserCountBrief> selectCommentCounts(@Param("ids") Collection<Long> ids);

    /** 批量统计点赞数：t_like.uk_target_user(target_type,target_id,user_id) 前缀可用 */
    @Select("<script>"
            + "SELECT target_id, COUNT(*) AS cnt FROM t_like "
            + "WHERE target_type = #{targetType} AND target_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY target_id"
            + "</script>")
    List<TargetCountBrief> selectLikeCounts(@Param("targetType") String targetType,
                                             @Param("ids") Collection<Long> ids);

    /** 我点过赞的评论 id 集合（用于回填 liked 状态） */
    @Select("<script>"
            + "SELECT target_id FROM t_like "
            + "WHERE target_type = #{targetType} AND user_id = #{userId} AND target_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Long> selectLikedTargetIds(@Param("targetType") String targetType,
                                    @Param("userId") Long userId,
                                    @Param("ids") Collection<Long> ids);
}

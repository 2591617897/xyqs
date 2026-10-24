package com.campus.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.user.dto.FollowUserVO;
import com.campus.user.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    /** 某用户的关注数（关系行实时统计，供对账） */
    @Select("select count(1) from t_follow where follower_id = #{followerId}")
    int countByFollower(@Param("followerId") Long followerId);

    /** 某用户的粉丝数（关系行实时统计，供对账） */
    @Select("select count(1) from t_follow where followee_id = #{followeeId}")
    int countByFollowee(@Param("followeeId") Long followeeId);

    /** 某关注关系是否存在（0=未关注，1=已关注；uk_pair 保证最多一行） */
    @Select("select count(1) from t_follow where follower_id = #{followerId} and followee_id = #{followeeId}")
    int countPair(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * 我关注的人列表。
     * me = 当前登录用户 id。返回按关注时间倒序的被关注者卡片，
     * isFollowing 在本列表里恒为 true（因为本来就是"我关注的"）；
     * followedBy 表示对方是否也回关了我（两者都为 true 即互相关注）。
     */
    @Select("""
        SELECT u.id AS id, u.nickname AS nickname, u.avatar AS avatar,
               u.school_id AS schoolId, u.verify_status AS verifyStatus,
               u.fans_count AS fansCount, u.follow_count AS followCount,
               true AS isFollowing,
               EXISTS(
                 SELECT 1 FROM t_follow fr
                 WHERE fr.follower_id = u.id AND fr.followee_id = #{me}
               ) AS followedBy
        FROM t_follow f
        INNER JOIN t_user u ON u.id = f.followee_id
        WHERE f.follower_id = #{me}
          AND u.status <> 0
        ORDER BY f.created_at DESC
        """)
    List<FollowUserVO> listFollowing(@Param("me") Long me);

    /**
     * 我的粉丝列表。
     * me = 当前登录用户 id。isFollowing 表示当前用户是否反过来关注了粉丝；
     * 粉丝列表里「对方关注我」恒成立，故 followedBy 恒为 true（isFollowing 即互关）。
     */
    @Select("""
        SELECT u.id AS id, u.nickname AS nickname, u.avatar AS avatar,
               u.school_id AS schoolId, u.verify_status AS verifyStatus,
               u.fans_count AS fansCount, u.follow_count AS followCount,
               EXISTS(
                 SELECT 1 FROM t_follow fx
                 WHERE fx.follower_id = #{me} AND fx.followee_id = u.id
               ) AS isFollowing,
               true AS followedBy
        FROM t_follow f
        INNER JOIN t_user u ON u.id = f.follower_id
        WHERE f.followee_id = #{me}
          AND u.status <> 0
        ORDER BY f.created_at DESC
        """)
    List<FollowUserVO> listFans(@Param("me") Long me);
}

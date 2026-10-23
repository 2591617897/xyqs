package com.campus.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.user.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}

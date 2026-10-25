package com.campus.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 只读用户 openid。
 *
 * 内容安全接口（msg_sec_check v2）要求带一个「近期访问过小程序」的 openid，
 * 而 JWT 里只有 userId / schoolId / verifyStatus，UserBrief 也不含 openid，
 * 所以这里必须直连 t_user 取。跨模块直读他表是本项目的既有做法
 * （见 campus-goods 的 UserSnapshotMapper）。
 */
@Mapper
public interface WxOpenidMapper {

    @Select("SELECT openid FROM t_user WHERE id = #{id}")
    String selectOpenid(@Param("id") Long id);

    /**
     * 随便取一个微信 openid，仅供 /internal/security/selfcheck 做连通性探测。
     *
     * <p>msg_sec_check v2 强制要求 openid，自检时不一定有现成的 userId 可传，
     * 拿库里任意一个近期的微信用户即可走通整条链路。
     */
    @Select("SELECT openid FROM t_user WHERE platform = 'wx' AND openid IS NOT NULL "
            + "AND openid <> '' ORDER BY id DESC LIMIT 1")
    String selectAnyOpenid();
}

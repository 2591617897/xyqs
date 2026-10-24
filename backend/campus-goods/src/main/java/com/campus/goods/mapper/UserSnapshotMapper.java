package com.campus.goods.mapper;

import com.campus.common.client.dto.UserBrief;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 读取 t_user 的最新昵称/头像（只取展示需要的两列）。
 *
 * 商品表的 seller_name / seller_avatar 是**发布时的快照**，卖家改昵称或头像后，
 * 列表与详情仍显示旧值。查询时用这里的结果覆盖快照即可（同一库跨表只读，
 * 与 SchoolRegionMapper 读 t_school 的做法一致），一次 IN 查询即可，无 N+1。
 */
@Mapper
public interface UserSnapshotMapper {

    @Select("<script>"
            + "SELECT id, nickname, avatar FROM t_user WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<UserBrief> selectBriefByIds(@Param("ids") Collection<Long> ids);
}

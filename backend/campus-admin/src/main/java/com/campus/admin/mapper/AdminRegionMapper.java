package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.entity.AdminRegion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 组织架构。
 *
 * <p>这里的查询服务于数据权限：把「管理员负责的节点集合」翻译成
 * 「可见的 t_school.id 集合」，一次查询，避免逐层递归打 N 次库。
 */
@Mapper
public interface AdminRegionMapper extends BaseMapper<AdminRegion> {

    /**
     * 取一批组织节点下辖的全部学校 id。
     *
     * <p>节点自身是校区就直接取其 {@code ref_school_id}；
     * 节点是地区则按省/市去 t_school 里反查（地区节点自身 ref_school_id 为 NULL）。
     *
     * @param regionIds 组织节点 id 集合（不含子孙，调用方负责展开；当前版本 flat 组织足够）
     */
    @Select("<script>"
            + "SELECT DISTINCT id FROM t_school WHERE "
            + "  <foreach collection='regionIds' item='r' open='(' separator=' OR ' close=')'>"
            + "    EXISTS (SELECT 1 FROM t_admin_region g WHERE g.id = #{r} AND g.type = 2 AND g.ref_school_id = t_school.id) "
            + "    OR ("
            + "      SELECT COUNT(*) FROM t_admin_region g2 WHERE g2.id = #{r} AND g2.type = 1 "
            + "        AND t_school.city = g2.city AND t_school.province = g2.province"
            + "    ) > 0"
            + "  </foreach>"
            + "</script>")
    List<Long> selectSchoolIdsByRegions(@Param("regionIds") List<Long> regionIds);
}

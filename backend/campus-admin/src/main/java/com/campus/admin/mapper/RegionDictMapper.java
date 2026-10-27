package com.campus.admin.mapper;

import com.campus.admin.dto.SchoolOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 组织节点表单的选项字典（只读，跨模块直读 t_school）。
 *
 * <p>跨模块直读他表是本项目的既有做法（见 campus-goods 的 UserSnapshotMapper）：
 * 学校字典归 campus-auth/campus-user 维护，后台这里只取下拉所需的几个字段。
 */
@Mapper
public interface RegionDictMapper {

    /**
     * 省 → 市 的去重组合。
     *
     * <p>去重而不是建行政区划表，是为了让「能选的值」天然等于「t_school 里存在的值」，
     * 从源头消灭手输拼错导致辖区匹配为空的问题。
     */
    @Select("SELECT DISTINCT province, city FROM t_school "
            + "WHERE province IS NOT NULL AND province <> '' "
            + "ORDER BY province, city")
    List<Map<String, Object>> selectProvinceCity();

    /** 校区下拉：可按省市过滤、按名称模糊搜索 */
    @Select("<script>"
            + "SELECT id, name, province, city FROM t_school WHERE 1 = 1"
            + "<if test=\"province != null and province != ''\"> AND province = #{province}</if>"
            + "<if test=\"city != null and city != ''\"> AND city = #{city}</if>"
            + "<if test=\"keywords != null and keywords.size() &gt; 0\">"
            + " <foreach collection=\"keywords\" item=\"kw\"> AND name LIKE CONCAT('%', #{kw}, '%')</foreach></if>"
            + " ORDER BY sort DESC, id ASC"
            + "</script>")
    List<SchoolOption> selectSchools(@Param("province") String province,
                                     @Param("city") String city,
                                     @Param("keywords") List<String> keywords);
}

package com.campus.wall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 学校区域查询（共享库直查 t_school 的省/市/区字段）。
 * 市域表白流：先按城市拿到学校 id 列表，再按 school_id 过滤。
 */
@Mapper
public interface SchoolRegionMapper {

    @Select("select city from t_school where id = #{schoolId}")
    String selectCityBySchoolId(@Param("schoolId") Long schoolId);

    @Select("select id from t_school where city = #{city}")
    List<Long> selectSchoolIdsByCity(@Param("city") String city);
}

package com.campus.goods.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 学校区域查询（共享库直查 t_school 的 city 字段）。
 * 市域闲置流：先按城市拿到学校 id 列表，再按 school_id 过滤商品。
 */
@Mapper
public interface SchoolRegionMapper {

    @Select("select city from t_school where id = #{schoolId}")
    String selectCityBySchoolId(@Param("schoolId") Long schoolId);

    @Select("select id from t_school where city = #{city}")
    List<Long> selectSchoolIdsByCity(@Param("city") String city);
}
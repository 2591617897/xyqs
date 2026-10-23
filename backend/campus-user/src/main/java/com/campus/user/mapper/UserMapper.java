package com.campus.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select name from t_school where id = #{schoolId}")
    String selectSchoolName(@Param("schoolId") Long schoolId);

    @Select("select count(1) from t_school where id = #{schoolId}")
    int countSchool(@Param("schoolId") Long schoolId);

    @Select("select count(1) from t_user where school_id = #{schoolId} and student_no = #{studentNo} "
            + "and verify_status = 2 and id <> #{excludeId}")
    int countStudentNoUsed(@Param("schoolId") Long schoolId,
                           @Param("studentNo") String studentNo,
                           @Param("excludeId") Long excludeId);
}

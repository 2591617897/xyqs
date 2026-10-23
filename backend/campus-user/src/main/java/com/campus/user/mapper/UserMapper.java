package com.campus.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.user.dto.SchoolRegion;
import com.campus.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 粉丝数增减（greatest 防负数，与 PostMapper.incrLike 同款写法） */
    @Update("update t_user set fans_count = greatest(fans_count + #{delta}, 0) where id = #{id}")
    int incrFans(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 加锁读粉丝数（current read，读最新已提交值）。
     * 用于 follow() 的 uk_pair 并发兜底路径：该路径需绕过事务快照，
     * 取到并发事务已提交的计数，避免返回旧值。
     */
    @Select("select fans_count from t_user where id = #{id} for update")
    Integer selectFansCountForUpdate(@Param("id") Long id);

    /** 关注数增减（greatest 防负数） */
    @Update("update t_user set follow_count = greatest(follow_count + #{delta}, 0) where id = #{id}")
    int incrFollow(@Param("id") Long id, @Param("delta") int delta);

    @Select("select name from t_school where id = #{schoolId}")
    String selectSchoolName(@Param("schoolId") Long schoolId);

    @Select("select province, city, district from t_school where id = #{schoolId}")
    SchoolRegion selectSchoolRegion(@Param("schoolId") Long schoolId);

    @Select("select count(1) from t_school where id = #{schoolId}")
    int countSchool(@Param("schoolId") Long schoolId);

    @Select("select count(1) from t_user where school_id = #{schoolId} and student_no = #{studentNo} "
            + "and verify_status = 2 and id <> #{excludeId}")
    int countStudentNoUsed(@Param("schoolId") Long schoolId,
                           @Param("studentNo") String studentNo,
                           @Param("excludeId") Long excludeId);
}

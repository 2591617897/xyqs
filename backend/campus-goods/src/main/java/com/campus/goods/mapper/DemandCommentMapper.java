package com.campus.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.goods.entity.DemandComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DemandCommentMapper extends BaseMapper<DemandComment> {

    @Update("update t_demand_comment set like_count = greatest(like_count + #{delta}, 0) where id = #{id}")
    int incrLike(@Param("id") Long id, @Param("delta") int delta);
}

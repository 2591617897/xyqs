package com.campus.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.goods.entity.Demand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DemandMapper extends BaseMapper<Demand> {

    @Update("update t_demand set view_count = view_count + 1 where id = #{id}")
    int incrView(@Param("id") Long id);

    @Update("update t_demand set reply_count = greatest(reply_count + #{delta}, 0) where id = #{id}")
    int incrReply(@Param("id") Long id, @Param("delta") int delta);
}

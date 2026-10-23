package com.campus.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.goods.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    @Update("update t_goods set view_count = view_count + 1 where id = #{id}")
    int incrView(@Param("id") Long id);

    @Update("update t_goods set like_count = greatest(like_count + #{delta}, 0) where id = #{id}")
    int incrLike(@Param("id") Long id, @Param("delta") int delta);

    @Update("update t_goods set comment_count = greatest(comment_count + #{delta}, 0) where id = #{id}")
    int incrComment(@Param("id") Long id, @Param("delta") int delta);
}

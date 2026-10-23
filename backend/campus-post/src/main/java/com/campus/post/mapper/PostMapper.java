package com.campus.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.post.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Update("update t_post set like_count = greatest(like_count + #{delta}, 0) where id = #{id}")
    int incrLike(@Param("id") Long id, @Param("delta") int delta);

    @Update("update t_post set comment_count = greatest(comment_count + #{delta}, 0) where id = #{id}")
    int incrComment(@Param("id") Long id, @Param("delta") int delta);
}

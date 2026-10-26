package com.campus.promote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.promote.entity.PromoteRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PromoteRecordMapper extends BaseMapper<PromoteRecord> {

    /**
     * 待重算的已结算记录：只捞「还没过期太久」的。
     *
     * <p>为什么加 {@code expire_at > now - 1 天}：已过期超过 1 天的记录，
     * 上一次重算时必然已经把它算成 0 并写回内容表，再算一次没有意义。
     * 不加上界，衰减任务会随着流水积累越来越慢。
     */
    @Select("select * from t_promote_record "
            + "where status = 1 and expire_at > #{since}")
    List<PromoteRecord> selectToRefresh(@Param("since") LocalDateTime since);

    /**
     * 某个内容当前仍有效的记录（用于算有效分）。
     */
    @Select("select * from t_promote_record "
            + "where target_type = #{targetType} and target_id = #{targetId} "
            + "and status = 1 and expire_at > #{now} order by expire_at asc")
    List<PromoteRecord> selectEffective(@Param("targetType") String targetType,
                                        @Param("targetId") Long targetId,
                                        @Param("now") LocalDateTime now);
}

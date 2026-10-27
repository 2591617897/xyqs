package com.campus.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.goods.entity.GoodsSearchLog;
import lombok.Data;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface GoodsSearchLogMapper extends BaseMapper<GoodsSearchLog> {

    /**
     * 写一条搜索日志，同一人同一天同一个词重复搜直接忽略。
     *
     * <p>用 {@code INSERT IGNORE} 而不是 {@code insert}：唯一键冲突是<b>预期内</b>的正常路径
     * （用户隔一会儿再搜同一个词），抛 {@code DuplicateKeyException} 会让日志里全是异常栈。
     * 顺带一个好处是「刷词」彻底失效——一个人一天只能给一个词贡献 1。
     */
    @Insert("""
            INSERT IGNORE INTO t_goods_search_log
                (id, keyword, norm_keyword, user_id, school_id, city, result_count, search_day)
            VALUES
                (#{id}, #{keyword}, #{normKeyword}, #{userId}, #{schoolId}, #{city}, #{resultCount}, #{searchDay})
            """)
    int insertIgnore(GoodsSearchLog row);

    /**
     * 热词榜：近 N 天「搜出过东西」的关键词，按搜索人次倒序。
     *
     * <p>{@code result_count > 0} 这条过滤很关键：热词点进去必须有结果。
     * 把「搜了 0 条」的词捧上榜首，等于把用户往空列表里推。
     *
     * <p>{@code HAVING COUNT(*) >= minHits} 用来滤掉「一个人手滑搜了一次」的偶发词。
     */
    @Select("""
            SELECT norm_keyword AS word, COUNT(*) AS hits
            FROM t_goods_search_log
            WHERE search_day >= #{from}
              AND result_count > 0
              AND CHAR_LENGTH(norm_keyword) >= 2
            GROUP BY norm_keyword
            HAVING COUNT(*) >= #{minHits}
            ORDER BY hits DESC, MAX(id) DESC
            LIMIT #{limit}
            """)
    List<HotWordRow> topWords(@Param("from") LocalDate from,
                              @Param("minHits") int minHits,
                              @Param("limit") int limit);

    /** 清理过期日志；由定时任务按保留天数调用 */
    @Delete("DELETE FROM t_goods_search_log WHERE search_day < #{before}")
    int deleteBefore(@Param("before") LocalDate before);

    /** 聚合结果行 */
    @Data
    class HotWordRow {

        /** 归一化关键词 */
        private String word;

        /** 近 N 天内有多少「人次」搜过（同人同日同词只算 1） */
        private Integer hits;
    }
}

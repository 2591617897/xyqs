package com.campus.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.admin.dto.NoticeStatVO;
import com.campus.admin.entity.AdminNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminNoticeMapper extends BaseMapper<AdminNotice> {

    /**
     * 批量取一组公告的弹窗触达统计。
     *
     * <p>一次 IN 查询取完整页，不要在循环里逐条 count（一页 10 条就是 10 次往返）。
     *
     * <p>用 {@code COUNT(CASE WHEN ... THEN 1 END)} 而不是 SUM：SUM 返回 BigDecimal，
     * 走 Long 字段要做隐式转换，COUNT 直接就是 BIGINT，少一层坑。
     */
    @Select("<script>"
            + "SELECT notice_id AS noticeId, "
            + "       COUNT(*) AS shownCount, "
            + "       COUNT(CASE WHEN confirmed_at IS NOT NULL THEN 1 END) AS confirmCount "
            + "FROM t_notice_receipt "
            + "WHERE notice_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY notice_id"
            + "</script>")
    List<NoticeStatVO> selectReceiptStats(@Param("ids") List<Long> ids);
}

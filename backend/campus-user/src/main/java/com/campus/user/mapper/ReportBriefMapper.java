package com.campus.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 举报/工单的内容摘要反查。
 *
 * <p>为什么要单独一个 Mapper：
 * 举报工单存在 t_report_ticket（campus-user 持有），但被举报的内容分散在
 * t_post / t_wall_post / t_goods / t_comment* / t_message 这些别人的表里。
 * 这些服务和 campus-user 共用同一个库，所以提工单时顺手取一份纯文本摘要写进
 * target_brief，运营在后台列表里就能直接看到「被举报的到底是什么」，
 * 不必再拿 id 去别的菜单翻。
 *
 * <p>只 SELECT，不做任何写操作；内容被删后摘要会取不到（返回 null），
 * 这是可接受的——摘要只是工单受理时的快照，内容已删除本身也是一种结论。
 *
 * <p>若将来拆库（各服务独立数据源），这里的 SQL 会全部失效，
 * 届时必须改成 Feign 调各服务的 internal 查询接口。
 */
@Mapper
public interface ReportBriefMapper {

    @Select("SELECT CONCAT_WS(' / ', NULLIF(title,''), NULLIF(content,'')) FROM t_post WHERE id = #{id}")
    String post(@Param("id") Long id);

    @Select("SELECT content FROM t_wall_post WHERE id = #{id}")
    String wall(@Param("id") Long id);

    @Select("SELECT CONCAT_WS(' / ', NULLIF(title,''), NULLIF(description,'')) FROM t_goods WHERE id = #{id}")
    String goods(@Param("id") Long id);

    @Select("SELECT nickname FROM t_user WHERE id = #{id}")
    String user(@Param("id") Long id);

    @Select("SELECT content FROM t_message WHERE id = #{id}")
    String message(@Param("id") Long id);

    /** 校友群消息（群聊长按举报）；取到的是发送时的正文快照 */
    @Select("SELECT content FROM t_group_message WHERE id = #{id}")
    String groupMsg(@Param("id") Long id);

    /** 评论：三张评论表按 id 依次找，t_post_comment 优先 */
    @Select("SELECT content FROM t_post_comment WHERE id = #{id}")
    String postComment(@Param("id") Long id);

    @Select("SELECT content FROM t_wall_comment WHERE id = #{id}")
    String wallComment(@Param("id") Long id);

    @Select("SELECT content FROM t_goods_comment WHERE id = #{id}")
    String goodsComment(@Param("id") Long id);
}

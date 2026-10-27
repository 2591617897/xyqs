package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 校友群定义。一所学校一个群，{@code type=1, bizKey=schoolId 字符串}。
 *
 * <p>这里同时承载三类「配置」：
 * <ul>
 *   <li>管控：{@code rateGapSec} / {@code rateDayMax}（0 = 按群规模自适应，后台可强制覆盖）；
 *       夜间静音的开关与时段（默认 00:30–06:30，后台可按群单独改）；</li>
 *   <li>冗余：{@code memberCount} / {@code lastMsg*}，避免列表页每次 COUNT 与子查询；</li>
 *   <li><b>零写放大</b>：{@code lastAtAllMsgId} 记录最近一次「@所有人」的消息 id，
 *       成员未读判断只做一次主键比较，绝不给一万个成员各写一行。</li>
 * </ul>
 */
@Data
@TableName("t_group")
public class Group {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 1 校友群 2 同城群（预留，本轮不产出数据） */
    private Integer type;

    /** type=1 时存 school_id 字符串，配合 uk_type_key 兜并发建群 */
    private String bizKey;

    private String name;

    private String avatar;

    private String notice;

    private Long schoolId;

    private Integer memberCount;

    private Long lastMsgId;

    private String lastMsgText;

    private LocalDateTime lastMsgTime;

    /** @所有人 的最新消息 id：> 我的 last_read_id 即「有未读的 @全体」 */
    private Long lastAtAllMsgId;

    /** 发言间隔秒，0 = 按群规模自适应 */
    private Integer rateGapSec;

    /** 每日发言上限，0 = 按群规模自适应 */
    private Integer rateDayMax;

    /** 夜间静音开关：1 开 0 关（后台可按群关闭） */
    private Integer nightEnabled;

    /** 夜间静音开始 HH:mm */
    private String nightStart;

    /** 夜间静音结束 HH:mm，允许跨天 */
    private String nightEnd;

    /** 1 正常 0 停用（停用后不可发言，只读） */
    private Integer status;

    /** 未来拆子群预留，现恒 NULL */
    private Long parentGroupId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

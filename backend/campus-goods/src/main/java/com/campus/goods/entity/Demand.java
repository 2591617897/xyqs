package com.campus.goods.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 校园需求（求物 / 求帮忙）。
 *
 * <p>与 {@link Goods} 的关系：需求是闲置的反向流 —— 闲置是「我有东西要出手」，
 * 需求是「我想要某个东西 / 想让某件事被做」。两者字段与状态机都是反的，
 * 所以各自一张表，不做 flow 字段混存。
 *
 * <p>共用的东西（不是各写一份）：
 * <ul>
 *   <li>分类白名单 —— {@code DemandService.DEMAND_CATEGORIES}</li>
 *   <li>可见范围 {@code visibility} —— {@code com.campus.common.content.Visibility}</li>
 *   <li>UGC 安全 —— {@code SecurityGuard.BIZ_DEMAND}</li>
 * </ul>
 */
@Data
@TableName("t_demand")
public class Demand {

    /** 1 求物：想要某个东西 */
    public static final int TYPE_WANT = 1;
    /** 2 求帮忙：想让某件事被做 */
    public static final int TYPE_HELP = 2;

    /** 0 已下架 */
    public static final int STATUS_OFF = 0;
    /** 1 进行中（列表可见、可回应） */
    public static final int STATUS_OPEN = 1;
    /** 2 已完成（已找到 / 已解决） */
    public static final int STATUS_DONE = 2;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long schoolId;

    /** 发布者昵称快照，避免列表页跨服务查询 */
    private String authorName;

    private String authorAvatar;

    /** 1求物 2求帮忙 */
    private Integer type;

    /** 想要什么 / 想做什么 */
    private String title;

    /** 具体要求 */
    private String description;

    /** 需求分类，见 DemandService.DEMAND_CATEGORIES */
    private Integer categoryId;

    /** 预算 / 报酬；NULL 表示面议 */
    private BigDecimal budget;

    /** 期望时间，如：本周内 / 今天下午 */
    private String expectTime;

    /** 交接地点 */
    private String location;

    /** 逗号分隔的参考图地址 */
    private String images;

    /**
     * 可见范围：school 仅本校 / city 同市可见（默认 school）。
     * 见 {@link com.campus.common.content.Visibility}。
     */
    private String visibility;

    /** 0已下架 1进行中 2已完成 */
    private Integer status;

    private Integer viewCount;

    private Integer replyCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private String categoryName;

    /** 当前浏览者是不是发布者（详情页控制操作按钮） */
    @TableField(exist = false)
    private Boolean mine;

    /** 当前浏览者是否已经回应过（详情页控制按钮文案） */
    @TableField(exist = false)
    private Boolean replied;

    /** 发布者头像（与 authorAvatar 同源，前端统一读它） */
    @TableField(exist = false)
    private String typeName;
}

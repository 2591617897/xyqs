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

@Data
@TableName("t_goods")
public class Goods {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long schoolId;

    /** 卖家昵称快照，避免列表页跨服务查询 */
    private String sellerName;

    private String sellerAvatar;

    private String title;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    /** 1数码 2图书 3生活 4服饰 5运动 6其他 */
    private Integer categoryId;

    /** 成色: 1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹，老数据为 null */
    private Integer conditionLevel;

    /** 入手时间，展示文案如：2023年9月 */
    private String purchaseTime;

    /** 使用频率：每天使用/经常使用/偶尔使用/几乎没用过 */
    private String usageFrequency;

    /** 来源渠道：自购自用/朋友赠送/活动奖品/实验室配发/其他 */
    private String source;

    /** 保修状态：官方保修中/官方保修已过/不确定 */
    private String warranty;

    /** 是否支持面交试机 0否 1是 */
    private Integer supportTrial;

    /** 逗号分隔的图片地址 */
    private String images;

    /** 1校内自提 2邮寄 */
    private Integer tradeType;

    private String location;

    /** 0已下架 1在售 2已售出 */
    private Integer status;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private String categoryName;

    /** 卖家实时在售数量（详情页统计，不入库） */
    @TableField(exist = false)
    private Integer sellerSaleCount;

    /** 卖家累计发布数量（详情页统计，不入库） */
    @TableField(exist = false)
    private Integer sellerTotalCount;
}

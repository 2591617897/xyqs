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

    /** 逗号分隔的图片地址 */
    private String images;

    /** 1校内自提 2邮寄 */
    private Integer tradeType;

    private String location;

    /** 0已下架 1在售 2已售出 */
    private Integer status;

    private Integer viewCount;

    private Integer likeCount;

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
}

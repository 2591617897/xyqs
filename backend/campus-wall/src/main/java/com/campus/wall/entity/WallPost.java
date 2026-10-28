package com.campus.wall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_wall_post")
public class WallPost {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仅入库用于风控追溯，绝不返回给客户端 */
    @JsonIgnore
    private Long userId;

    private Long schoolId;

    /** 对外展示的匿名昵称：匿名同学·037 */
    private String anonName;

    /** 匿名头像序号 1-8，前端据此上色 */
    private Integer anonAvatar;

    private String content;

    private String images;

    /** 分类：love 精选表白 / tree 树洞吐槽 / find 寻物启事 / findp 捞人 */
    private String category;

    private Integer likeCount;

    private Integer commentCount;

    /** 浏览量：详情接口每次访问 +1，列表直接取库值 */
    private Integer viewCount;

    private Integer status;

    /**
     * 当前有效推广分。由 campus-promote 看广告后回写，并按 7 天线性衰减重算。
     * 列表第一排序键，必须落库。
     */
    private Integer promoteScore;

    /** 最晚一条推广的到期时间；0 分为 null */
    private LocalDateTime promoteExpireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private Boolean liked;

    /** 是否是当前用户发的（用于展示删除按钮），不暴露 user_id */
    @TableField(exist = false)
    private Boolean mine;
}

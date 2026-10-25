package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词。level 决定命中后如何处理：1仅记录 2替换 3禁止发布。
 */
@Data
@TableName("t_sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String word;

    /** 1提示（仅记录） 2替换 3禁止发布 */
    private Integer level;

    private String replaceTo;

    private String category;

    /** 1启用 0停用 */
    private Integer status;

    private Integer hitCount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

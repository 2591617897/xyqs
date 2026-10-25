package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 校区/地区负责人。
 */
@Data
@TableName("t_admin_region_leader")
public class AdminRegionLeader {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long regionId;

    private Long adminUserId;

    /** 1负责人 2协管 */
    private Integer leaderType;

    /** 1生效 0已撤销 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

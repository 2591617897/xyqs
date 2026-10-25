package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台组织架构节点（地区 / 校区树）。
 *
 * <p>type=2 的校区节点用 {@code refSchoolId} 软关联 t_school（不建外键）；
 * type=1 的地区节点本身没有 school_id，靠 province/city 去 t_school 里反查辖区。
 */
@Data
@TableName("t_admin_region")
public class AdminRegion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;

    private String name;

    /** 1地区 2校区 */
    private Integer type;

    /** 1省 2市 3校区 */
    private Integer level;

    private String province;

    private String city;

    private Long refSchoolId;

    private Integer sort;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

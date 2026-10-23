package com.campus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_school")
public class School {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String name;

    private String province;

    private String city;

    /** 所属区县（区域定位展示） */
    private String district;

    private String logo;

    private Integer sort;

    private LocalDateTime createdAt;
}

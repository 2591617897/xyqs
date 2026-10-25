package com.campus.admin.dto;

import lombok.Data;

/**
 * 校区下拉选项（组织节点「关联校区」用）。
 *
 * <p>不再让管理员手填 t_school.id：那是 19 位雪花值，肉眼核对不了，
 * 填错就是「节点挂到了别的学校」，而且删节点前的重复校验也会失效。
 * 列表带 province/city，便于选中后自动回填节点的地区字段。
 */
@Data
public class SchoolOption {

    private Long id;

    private String name;

    private String province;

    private String city;
}

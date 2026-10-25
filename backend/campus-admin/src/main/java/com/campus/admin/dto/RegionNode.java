package com.campus.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织树节点。比 AdminRegion 多一个 children，供 el-tree 直接渲染。
 */
@Data
public class RegionNode {

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

    private List<RegionNode> children = new ArrayList<>();
}

package com.campus.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点。后端下发，前端既用它渲染侧边栏，也用它 addRoute 生成动态路由。
 */
@Data
public class MenuNode {

    private Long id;

    private Long parentId;

    private String name;

    /** 1目录 2菜单 3按钮 */
    private Integer type;

    /** 前端路由路径 */
    private String path;

    /** 前端组件相对路径，如 content/PostList */
    private String component;

    /** Element Plus 图标组件名，如 Odometer */
    private String icon;

    private String permission;

    private Integer sort;

    private Integer visible;

    private Integer status;

    private List<MenuNode> children = new ArrayList<>();
}

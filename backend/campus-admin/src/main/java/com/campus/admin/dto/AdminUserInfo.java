package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 当前登录管理员信息，含角色与全部权限点。
 */
@Data
public class AdminUserInfo {

    private Long id;

    private String username;

    private String realName;

    private String avatar;

    private boolean superFlag;

    /** 1全部 2仅辖区 */
    private Integer dataScope;

    private boolean mustChangePwd;

    private LocalDateTime lastLoginAt;

    private List<RoleBrief> roles;

    /** 超管返回空集合（拦截器直接放行），前端同样按超管处理 */
    private Set<String> permissions;

    @Data
    public static class RoleBrief {
        private Long id;
        private String name;
        private String code;
    }
}

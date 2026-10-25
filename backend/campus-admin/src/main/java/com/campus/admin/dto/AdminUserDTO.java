package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员列表/详情出参。刻意不含 password / salt。
 */
@Data
public class AdminUserDTO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String email;

    private String avatar;

    private Integer superFlag;

    private Integer dataScope;

    private Integer status;

    private Integer mustChangePwd;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    private LocalDateTime createdAt;

    private List<Long> roleIds;
}

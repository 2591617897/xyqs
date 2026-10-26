package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员。与小程序用户体系（t_user）完全无关的独立账号。
 */
@Data
@TableName("t_admin_user")
public class AdminUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String password;

    private String salt;

    private String realName;

    private String phone;

    private String email;

    private String avatar;

    /** 1超级管理员（免权限校验、全量数据权限） 0普通管理员 */
    private Integer superFlag;

    /** 数据权限范围 1全部 2仅辖区 */
    private Integer dataScope;

    /** 1正常 0禁用 */
    private Integer status;

    /** 1强制修改初始密码 0已修改 */
    private Integer mustChangePwd;

    /**
     * 令牌版本号：改密、重置、禁用、删除时 +1。
     *
     * <p>JWT 是无状态的，服务端没法「收回」一张已发出的令牌；把版本号写进令牌 claim，
     * 每次请求与库里的值比对，一旦库里的值被 +1，所有旧令牌当场失效。
     * 这是无状态令牌实现「立即踢下线」的标准做法，代价是每个请求多一次主键查询（本地缓存 10 秒）。
     */
    private Integer tokenVersion;

    private Integer failCount;

    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

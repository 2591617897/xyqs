package com.campus.admin.dto;

import lombok.Data;

/**
 * 管理员账号的「鉴权状态」轻量快照，只查 {@code t_admin_user} 的三个小字段。
 *
 * <p>鉴权链路上每个请求都要用它，所以刻意只查这三列，不做 {@code select *} ——
 * 既避免把 BCrypt 哈希读进内存，也让这条查询永远走主键覆盖索引。
 */
@Data
public class AdminAuthState {

    /** 1正常 0禁用 */
    private Integer status;

    /** 令牌版本，与令牌 claim {@code tv} 比对 */
    private Integer tokenVersion;

    /** 1=必须先改初始密码，此时只放行改密等少数接口 */
    private Integer mustChangePwd;
}

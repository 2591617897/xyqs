package com.campus.admin.config;

import lombok.Data;

import java.util.List;

/**
 * 数据权限范围。
 *
 * <p>all=true 表示不限校区；否则只能看到 {@code schoolIds} 范围内的数据。
 * {@code schoolIds} 为空列表意味着「看不到任何数据」，而不是「不限」——
 * 后者会把后台数据全部泄漏给一个没配辖区的负责人账号。
 */
@Data
public class DataScope {

    private boolean all;

    private List<Long> schoolIds;

    public static DataScope all() {
        DataScope ds = new DataScope();
        ds.setAll(true);
        ds.setSchoolIds(List.of());
        return ds;
    }

    public static DataScope empty() {
        DataScope ds = new DataScope();
        ds.setAll(false);
        ds.setSchoolIds(List.of());
        return ds;
    }

    public static DataScope of(List<Long> schoolIds) {
        DataScope ds = new DataScope();
        ds.setAll(false);
        ds.setSchoolIds(schoolIds);
        return ds;
    }
}

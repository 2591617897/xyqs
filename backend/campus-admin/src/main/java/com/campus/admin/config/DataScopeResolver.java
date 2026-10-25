package com.campus.admin.config;

import com.campus.admin.mapper.AdminRegionMapper;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据权限解析：把当前管理员翻译成「可见学校集合」。
 *
 * <p>超级管理员与 data_scope=1（全部）放行；data_scope=2（仅辖区）由其担任负责人的
 * 组织节点展开成 school_id 集合。辖区为空 = 看不到任何业务数据，
 * 这是刻意的设计——给一个没配辖区的负责人账号返回全量数据是严重越权。
 *
 * <p>Service 里的统一用法：
 * <pre>{@code
 * DataScope ds = dataScopeResolver.resolve();
 * if (!ds.isAll()) {
 *     if (ds.getSchoolIds().isEmpty()) return R.Page.of(List.of(), 0, page, size);
 *     wrapper.in(Post::getSchoolId, ds.getSchoolIds());
 * }
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class DataScopeResolver {

    private final AdminRegionMapper regionMapper;

    public DataScope resolve() {
        AdminContext.AdminUser u = AdminContext.get();
        if (u == null) {
            throw BizException.unauthorized();
        }
        if (u.superAdmin() || "ALL".equalsIgnoreCase(u.scope())) {
            return DataScope.all();
        }
        if (u.regionIds() == null || u.regionIds().isEmpty()) {
            return DataScope.empty();
        }
        return DataScope.of(regionMapper.selectSchoolIdsByRegions(u.regionIds()));
    }
}

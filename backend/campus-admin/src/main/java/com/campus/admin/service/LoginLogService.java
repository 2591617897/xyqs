package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.entity.AdminLoginLog;
import com.campus.admin.mapper.AdminLoginLogMapper;
import com.campus.common.api.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 后台登录日志查询。写入在 {@code AuthAdminService#login} 里同步完成
 * （登录 QPS 极低，不值得异步）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final AdminLoginLogMapper adminLoginLogMapper;

    public R.Page<AdminLoginLog> page(String username, Integer status,
                                      LocalDateTime start, LocalDateTime end, long page, long size) {
        Page<AdminLoginLog> p = adminLoginLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdminLoginLog>lambdaQuery()
                        .like(StringUtils.hasText(username), AdminLoginLog::getUsername, username)
                        .eq(status != null, AdminLoginLog::getStatus, status)
                        .ge(start != null, AdminLoginLog::getCreatedAt, start)
                        .le(end != null, AdminLoginLog::getCreatedAt, end)
                        .orderByDesc(AdminLoginLog::getCreatedAt));
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }
}

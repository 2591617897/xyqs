package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.entity.AdminOperationLog;
import com.campus.admin.mapper.AdminOperationLogMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作审计日志。写入由 AsyncAuditWriter 的守护线程批量调用 saveBatch，
 * 这里只负责「批量落库」与「后台查询」两件事。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final AdminOperationLogMapper adminOperationLogMapper;

    public R.Page<AdminOperationLog> page(Long adminId, String username, String module,
                                          Integer result, LocalDateTime start, LocalDateTime end,
                                          long page, long size) {
        Page<AdminOperationLog> p = adminOperationLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdminOperationLog>lambdaQuery()
                        .eq(adminId != null, AdminOperationLog::getAdminId, adminId)
                        .like(StringUtils.hasText(username), AdminOperationLog::getUsername, username)
                        .eq(StringUtils.hasText(module), AdminOperationLog::getModule, module)
                        .eq(result != null, AdminOperationLog::getResult, result)
                        .ge(start != null, AdminOperationLog::getCreatedAt, start)
                        .le(end != null, AdminOperationLog::getCreatedAt, end)
                        .orderByDesc(AdminOperationLog::getCreatedAt));
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public AdminOperationLog detail(Long id) {
        AdminOperationLog row = adminOperationLogMapper.selectById(id);
        if (row == null) {
            throw BizException.notFound("日志不存在");
        }
        return row;
    }

    /**
     * 批量落库。由异步写线程调用，失败向上抛让调用方记 error —— 绝不吞掉。
     */
    public void saveBatch(List<AdminOperationLog> records) {
        // MyBatis-Plus 的 ServiceImpl#saveBatch 在这里没有继承，直接循环 insert；
        // 批量条数固定 <= 64，开销可控。
        for (AdminOperationLog record : records) {
            adminOperationLogMapper.insert(record);
        }
    }
}

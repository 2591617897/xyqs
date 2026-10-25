package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.user.dto.ReportReq;
import com.campus.user.entity.ReportTicket;
import com.campus.user.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 提交举报 / 反馈（需登录，网关默认拦截） */
    @PostMapping
    public R<Long> create(@RequestBody @Valid ReportReq req) {
        return R.ok(reportService.create(req));
    }

    /** 我提交的举报 / 反馈 */
    @GetMapping("/mine")
    public R<List<ReportTicket>> mine() {
        return R.ok(reportService.listMine());
    }

    @GetMapping("/{id}")
    public R<ReportTicket> get(@PathVariable Long id) {
        return R.ok(reportService.getById(id));
    }
}

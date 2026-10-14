package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.service.ViolationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端违规情况（按用户聚合，非违规记录明细） */
@RestController
@RequestMapping("/api/admin/violations")
@RequiredArgsConstructor
public class AdminViolationController {

    private final ViolationService violationService;

    /** 按用户统计违规情况，支持按用户名/姓名搜索 */
    @GetMapping("/users")
    public Result<Map<String, Object>> users(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") Integer current,
                                             @RequestParam(defaultValue = "10") Integer size) {
        UserContext.requireAdmin();
        return Result.ok(violationService.userSummaryPage(keyword, current, size));
    }
}

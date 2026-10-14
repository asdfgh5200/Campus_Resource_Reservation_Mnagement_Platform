package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 管理端数据概览 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        UserContext.requireAdmin();
        return Result.ok(statsService.dashboard());
    }
}

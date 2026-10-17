package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局配置：一键闭馆开关。
 * GET  /api/system/campus-status 所有人可读，用于前端展示闭馆横幅
 * POST /api/system/campus-close  管理员：闭馆
 * POST /api/system/campus-open   管理员：恢复开放
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/campus-status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("closed", systemConfigService.isClosed());
        return Result.ok(body);
    }

    @PostMapping("/campus-close")
    public Result<Map<String, Object>> close() {
        UserContext.requireAdmin();
        systemConfigService.setClosed(true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("closed", true);
        return Result.ok(body);
    }

    @PostMapping("/campus-open")
    public Result<Map<String, Object>> open() {
        UserContext.requireAdmin();
        systemConfigService.setClosed(false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("closed", false);
        return Result.ok(body);
    }
}

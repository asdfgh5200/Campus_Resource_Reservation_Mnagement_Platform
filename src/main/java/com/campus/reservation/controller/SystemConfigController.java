package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ClosureDTO;
import com.campus.reservation.entity.CampusClosure;
import com.campus.reservation.service.ClosureService;
import com.campus.reservation.service.MessageService;
import com.campus.reservation.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final ClosureService closureService;
    private final MessageService messageService;

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
        // 向全体用户群发闭馆通知
        messageService.broadcast("【闭馆通知】校园预约系统已由管理员执行闭馆，普通用户暂停预约与使用，重新开放时间请关注系统横幅或再次收到本通知。", MessageService.SCOPE_ALL);
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

    /** 细粒度闭馆：查询全部生效中的类型闭馆窗口（所有人可读，用于前端时段禁用展示） */
    @GetMapping("/closures")
    public Result<List<CampusClosure>> closures() {
        return Result.ok(closureService.listActive());
    }

    /** 细粒度闭馆：管理员创建，返回自动取消的预约数 */
    @PostMapping("/closures")
    public Result<Map<String, Object>> addClosure(@RequestBody ClosureDTO dto) {
        int cancelled = closureService.create(dto);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelled", cancelled);
        return Result.ok(body);
    }

    /** 细粒度闭馆：管理员撤销 */
    @DeleteMapping("/closures/{id}")
    public Result<Void> revokeClosure(@PathVariable Long id) {
        closureService.revoke(id);
        return Result.ok();
    }
}

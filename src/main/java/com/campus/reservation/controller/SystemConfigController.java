package com.campus.reservation.controller;

import com.campus.reservation.common.BizException;
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

    /** 闭馆原因上限：与维修驳回原因同量级，避免广播文案被刷屏 */
    private static final int REASON_MAX = 100;

    private final SystemConfigService systemConfigService;
    private final ClosureService closureService;
    private final MessageService messageService;

    @GetMapping("/campus-status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("closed", systemConfigService.isClosed());
        return Result.ok(body);
    }

    /** 一键闭馆：reason 可选（来自管理页或智约机器人草案），会写进群发文案让用户知道原因 */
    @PostMapping("/campus-close")
    public Result<Map<String, Object>> close(@RequestBody(required = false) Map<String, String> payload) {
        UserContext.requireAdmin();
        String reason = payload == null ? null : payload.get("reason");
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        if (reason != null && reason.length() > REASON_MAX) {
            throw BizException.badRequest("闭馆原因不能超过 " + REASON_MAX + " 字");
        }
        systemConfigService.setClosed(true);
        // 向全体用户群发闭馆通知，带上原因让人知道为什么闭馆
        messageService.broadcast(String.format(
                "【闭馆通知】校园预约服务现已暂停%s，暂停期间不可新建预约或使用资源，恢复开放时间将通过平台横幅与站内消息另行通知。",
                reason == null ? "" : "（原因：" + reason + "）"), MessageService.SCOPE_ALL);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("closed", true);
        body.put("reason", reason);
        return Result.ok(body);
    }

    /** 恢复开放：同样群发一条恢复通知，用户不用再自己试探能不能约 */
    @PostMapping("/campus-open")
    public Result<Map<String, Object>> open() {
        UserContext.requireAdmin();
        systemConfigService.setClosed(false);
        messageService.broadcast("【恢复开放通知】校园预约服务已恢复开放，可正常新建预约与使用资源，感谢您的理解与配合。",
                MessageService.SCOPE_ALL);
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

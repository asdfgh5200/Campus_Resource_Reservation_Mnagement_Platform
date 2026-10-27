package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 管理端「用户管理」：用户列表、用户详情（含预约/违规/留言记录）与资料维护 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户分页：仅普通用户，支持按用户名/姓名搜索 */
    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") Integer current,
                                            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(adminUserService.page(keyword, current, size));
    }

    /** 用户详情：传 startDate/endDate 可按时间筛选预约、违规与留言记录 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(adminUserService.detail(id, startDate, endDate));
    }

    /** 修改用户资料与数值：姓名 / 手机号 / 邮箱 / 账号状态 / 取消次数 */
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(adminUserService.update(id, body));
    }

    /** 给指定用户发送站内通知 */
    @PostMapping("/{id}/notify")
    public Result<Void> notify(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminUserService.notify(id, body.get("content"));
        return Result.ok();
    }
}

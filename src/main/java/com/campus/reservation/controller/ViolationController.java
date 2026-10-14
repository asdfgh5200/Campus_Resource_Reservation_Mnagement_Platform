package com.campus.reservation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ViolationDTO;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.service.ViolationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;

    /** 违规记录查询：userId 缺省时管理员查全部，普通用户强制查自己 */
    @GetMapping
    public Result<Page<Violation>> page(@RequestParam(required = false) Long userId,
                                        @RequestParam(defaultValue = "1") Integer current,
                                        @RequestParam(defaultValue = "10") Integer size) {
        UserContext.LoginUser ctx = UserContext.get();
        Long effective = (ctx != null && ctx.getRole() != null && ctx.getRole() == 1) ? userId : ctx.getUserId();
        return Result.ok(violationService.page(effective, current, size));
    }

    /** 手动登记违规（管理员） */
    @PostMapping
    public Result<Violation> add(@RequestBody ViolationDTO dto) {
        UserContext.requireAdmin();
        return Result.ok(violationService.add(dto));
    }
}

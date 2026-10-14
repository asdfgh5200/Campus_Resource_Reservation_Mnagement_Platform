package com.campus.reservation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.RepairDTO;
import com.campus.reservation.dto.RepairHandleDTO;
import com.campus.reservation.entity.RepairOrder;
import com.campus.reservation.service.RepairService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repairs")
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;

    /** 用户提交维修申请 */
    @PostMapping
    public Result<RepairOrder> create(@RequestBody RepairDTO dto) {
        return Result.ok(repairService.create(dto));
    }

    /** 维修工单列表（管理员） */
    @GetMapping
    public Result<Page<RepairOrder>> page(@RequestParam(required = false) Integer status,
                                          @RequestParam(defaultValue = "1") Integer current,
                                          @RequestParam(defaultValue = "10") Integer size) {
        UserContext.requireAdmin();
        return Result.ok(repairService.page(status, current, size));
    }

    /** 管理员处理工单 */
    @PutMapping("/{id}/handle")
    public Result<RepairOrder> handle(@PathVariable Long id, @RequestBody RepairHandleDTO dto) {
        UserContext.requireAdmin();
        return Result.ok(repairService.handle(id, dto));
    }
}

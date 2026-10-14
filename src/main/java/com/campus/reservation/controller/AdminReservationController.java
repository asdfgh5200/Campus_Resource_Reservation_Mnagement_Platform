package com.campus.reservation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.ReservationLog;
import com.campus.reservation.service.ReservationLogService;
import com.campus.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端：预约与操作日志查看 */
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationService reservationService;
    private final ReservationLogService reservationLogService;

    /** 全部预约（含用户名，可按用户名/状态/资源类型筛选） */
    @GetMapping("/page")
    public Result<Page<Reservation>> page(@RequestParam(defaultValue = "1") Integer current,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          @RequestParam(required = false) String username,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Integer resourceType) {
        UserContext.requireAdmin();
        return Result.ok(reservationService.adminPage(current, size, username, status, resourceType));
    }

    /** 预约操作日志（含用户名，可按用户名/操作类型筛选） */
    @GetMapping("/logs")
    public Result<Page<ReservationLog>> logs(@RequestParam(defaultValue = "1") Integer current,
                                             @RequestParam(defaultValue = "10") Integer size,
                                             @RequestParam(required = false) String username,
                                             @RequestParam(required = false) Integer action) {
        UserContext.requireAdmin();
        return Result.ok(reservationLogService.page(current, size, username, action));
    }
}

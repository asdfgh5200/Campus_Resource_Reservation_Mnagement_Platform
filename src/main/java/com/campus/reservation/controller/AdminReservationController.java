package com.campus.reservation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.ReservationLog;
import com.campus.reservation.service.ReservationLogService;
import com.campus.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 管理端：预约与操作日志查看 */
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationService reservationService;
    private final ReservationLogService reservationLogService;

    /** 全部预约（含用户名，可按用户名/状态/资源类型/校区/资源/日期筛选） */
    @GetMapping("/page")
    public Result<Page<Reservation>> page(@RequestParam(defaultValue = "1") Integer current,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          @RequestParam(required = false) String username,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Integer resourceType,
                                          @RequestParam(required = false) Long resourceId,
                                          @RequestParam(required = false) String date,
                                          @RequestParam(required = false) String campus,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate) {
        UserContext.requireAdmin();
        LocalDate day = StringUtils.hasText(date) ? LocalDate.parse(date) : null;
        LocalDate from = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : null;
        LocalDate to = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : null;
        return Result.ok(reservationService.adminPage(current, size, username, status, resourceType, resourceId, day,
                campus, from, to));
    }

    /** 预约操作日志（可按预约归属用户名、操作类型、操作人角色筛选；传 reservationId 看单条时间线） */
    @GetMapping("/logs")
    public Result<Page<ReservationLog>> logs(@RequestParam(defaultValue = "1") Integer current,
                                             @RequestParam(defaultValue = "10") Integer size,
                                             @RequestParam(required = false) String username,
                                             @RequestParam(required = false) Integer action,
                                             @RequestParam(required = false) Integer operatorRole,
                                             @RequestParam(required = false) Long reservationId) {
        UserContext.requireAdmin();
        return Result.ok(reservationLogService.page(current, size, username, action, operatorRole, reservationId));
    }

    /** 单条预约详情：含用户、资源、当日验证码与打卡坐标，供管理端查看完整信息 */
    @GetMapping("/{id}")
    public Result<Reservation> detail(@PathVariable Long id) {
        return Result.ok(reservationService.adminDetail(id));
    }

    /** 直接修改预约状态（用户管理详情里调整用户预约） */
    @PutMapping("/{id}/status")
    public Result<Reservation> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return Result.ok(reservationService.adminUpdateStatus(id, body.get("status")));
    }
}

package com.campus.reservation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.Result;
import com.campus.reservation.dto.ReservationDTO;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 创建预约（含时间冲突检测与规则校验） */
    @PostMapping
    public Result<Reservation> create(@RequestBody ReservationDTO dto) {
        return Result.ok(reservationService.create(dto));
    }

    /** 我的预约（分页，status 缺省查全部） */
    @GetMapping("/my")
    public Result<Page<Reservation>> my(@RequestParam(defaultValue = "1") Integer current,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) Integer status) {
        return Result.ok(reservationService.my(current, size, status));
    }

    /** 某资源某日已占用时段（用于前端时段条） */
    @GetMapping("/resource")
    public Result<List<Reservation>> listByResource(@RequestParam Long resourceId,
                                                     @RequestParam String date) {
        return Result.ok(reservationService.listByResource(resourceId, LocalDate.parse(date)));
    }

    /** 预约详情 */
    @GetMapping("/{id}")
    public Result<Reservation> detail(@PathVariable Long id) {
        return Result.ok(reservationService.detail(id));
    }

    /** 取消预约 */
    @PostMapping("/{id}/cancel")
    public Result<Reservation> cancel(@PathVariable Long id) {
        return Result.ok(reservationService.cancel(id));
    }

    /** 删除预约记录（仅限已结束的预约） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reservationService.delete(id);
        return Result.ok();
    }

    /** 提前释放空间（使用中的预约主动结束） */
    @PostMapping("/{id}/release")
    public Result<Reservation> release(@PathVariable Long id) {
        return Result.ok(reservationService.release(id));
    }

    /** 暂离（minutes=30 普通暂离；minutes=60 仅饭点可用） */
    @PostMapping("/{id}/away")
    public Result<Reservation> away(@PathVariable Long id,
                                    @RequestParam(required = false) Integer minutes) {
        return Result.ok(reservationService.away(id, minutes));
    }

    /** 结束暂离，恢复使用 */
    @PostMapping("/{id}/away/cancel")
    public Result<Reservation> cancelAway(@PathVariable Long id) {
        return Result.ok(reservationService.cancelAway(id));
    }
}

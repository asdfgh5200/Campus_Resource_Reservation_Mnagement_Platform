package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.HolidayDTO;
import com.campus.reservation.entity.Holiday;
import com.campus.reservation.service.HolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public Result<List<Holiday>> list() {
        return Result.ok(holidayService.list());
    }

    @PostMapping
    public Result<Holiday> add(@RequestBody HolidayDTO dto) {
        UserContext.requireAdmin();
        return Result.ok(holidayService.add(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserContext.requireAdmin();
        holidayService.delete(id);
        return Result.ok();
    }
}

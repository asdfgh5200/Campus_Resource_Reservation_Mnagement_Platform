package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.CheckinVerifyDTO;
import com.campus.reservation.service.CheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/checkins")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    /** 验证码打卡 */
    @PostMapping("/verify")
    public Result<?> verify(@RequestBody CheckinVerifyDTO dto) {
        return Result.ok(checkinService.verify(dto.getReservationId(), dto.getCode()));
    }

    /** 获取资源当日验证码（管理员/资源侧） */
    @GetMapping("/code/{resourceId}")
    public Result<Map<String, Object>> code(@PathVariable Long resourceId) {
        UserContext.requireAdmin();
        return Result.ok(checkinService.getCode(resourceId));
    }
}

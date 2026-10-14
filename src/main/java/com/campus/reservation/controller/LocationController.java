package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /** 附近可预约资源（按距离升序） */
    @GetMapping("/nearby")
    public Result<List<Map<String, Object>>> nearby(@RequestParam Double lat,
                                                    @RequestParam Double lng,
                                                    @RequestParam(required = false) Integer type,
                                                    @RequestParam(required = false) Double radius) {
        return Result.ok(locationService.nearby(lat, lng, type, radius));
    }
}

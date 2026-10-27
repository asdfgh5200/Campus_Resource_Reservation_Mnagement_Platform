package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.common.CheckinGeofence;
import com.campus.reservation.entity.CheckinCode;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final double EARTH_RADIUS = 6371000;

    private final CheckinCodeService checkinCodeService;
    private final ResourceService resourceService;
    private final ReservationService reservationService;

    /** 获取/生成资源当日验证码（管理员在资源现场公示） */
    public Map<String, Object> getCode(Long resourceId) {
        resourceService.getById(resourceId);
        CheckinCode code = checkinCodeService.ensureTodayCode(resourceId);
        Map<String, Object> data = new HashMap<>();
        data.put("resourceId", resourceId);
        data.put("code", code.getCode());
        data.put("validDate", code.getValidDate());
        data.put("expiresAt", code.getExpiresAt());
        return data;
    }

    /** 验证码打卡（无定位条件时人工补签，打卡方式记为 2） */
    public Object verify(Long reservationId, String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("请输入验证码");
        }
        CheckinCode checkinCode = checkinCodeService.findTodayCode(requireReservationResource(reservationId));
        if (checkinCode == null || !checkinCode.getCode().equals(code.trim())) {
            throw BizException.badRequest("验证码错误或已失效");
        }
        return reservationService.checkin(reservationId);
    }

    /**
     * 使用浏览器定位完成打卡。同时落库用户坐标、距离与精度，便于管理端追溯；
     * 这些坐标只在管理端预约详情中展示，用户端接口不回传。
     * 浏览器 Geolocation 使用 WGS-84，因此资源坐标也必须以 WGS-84 配置。
     */
    public Object verifyLocation(Long reservationId, Double latitude, Double longitude, Double accuracy) {
        if (latitude == null || longitude == null || accuracy == null) {
            throw BizException.badRequest("请授权浏览器定位后再打卡");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw BizException.badRequest("定位坐标无效");
        }
        if (accuracy <= 0 || accuracy > CheckinGeofence.MAX_ACCURACY_METERS) {
            throw BizException.badRequest("当前定位精度不足，请重试");
        }

        Long resourceId = requireReservationResource(reservationId);
        Resource resource = resourceService.getById(resourceId);
        if (resource.getLatitude() == null || resource.getLongitude() == null) {
            throw BizException.badRequest("该资源暂未设置打卡位置，请联系平台工作人员处理");
        }

        double targetLatitude = resource.getLatitude().doubleValue();
        double targetLongitude = resource.getLongitude().doubleValue();
        double distance = distance(latitude, longitude, targetLatitude, targetLongitude);
        double radius = CheckinGeofence.radiusOf(resource.getResourceType());
        // 按定位精度放宽：精度已限制在 100 米内，容差另设上限，避免漂移过大时跨楼误打卡
        double allowedRadius = CheckinGeofence.allowedRadius(radius, accuracy);
        String targetName = resource.getBuilding() == null || resource.getBuilding().isBlank()
                ? resource.getName() : resource.getBuilding();
        if (distance > allowedRadius) {
            throw BizException.badRequest(String.format(
                    "当前位置距%s约 %.0f 米，请靠近后再试",
                    targetName, distance));
        }

        Reservation reservation = reservationService.checkin(reservationId, Reservation.CHECKIN_TYPE_LOCATION,
                String.format("完成定位打卡（距%s %.0f 米，允许 %.0f 米，定位精度 %.0f 米）",
                        targetName, distance, allowedRadius, accuracy),
                BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                (int) Math.round(distance), (int) Math.round(accuracy));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reservation", reservation);
        data.put("distance", Math.round(distance));
        data.put("accuracy", Math.round(accuracy));
        data.put("radius", Math.round(radius));
        data.put("allowedRadius", Math.round(allowedRadius));
        return data;
    }

    private Long requireReservationResource(Long reservationId) {
        if (reservationId == null) {
            throw BizException.badRequest("预约 ID 不能为空");
        }
        var reservation = reservationService.detail(reservationId);
        return reservation.getResourceId();
    }

    /** Haversine 公式计算两点球面距离（米），供"附近资源推荐"使用 */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

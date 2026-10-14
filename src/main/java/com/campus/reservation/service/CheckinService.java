package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.entity.CheckinCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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

    /** 验证码打卡 */
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

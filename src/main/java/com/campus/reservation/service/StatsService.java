package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final SysUserMapper userMapper;

    /** 管理端数据概览 */
    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resourceTotal", resourceMapper.selectCount(null));
        data.put("resourceActive", resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().eq(Resource::getStatus, 1)));
        data.put("resourceRepair", resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().eq(Resource::getStatus, 3)));
        data.put("reservationTotal", reservationMapper.selectCount(null));
        data.put("reservationPending", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery().eq(Reservation::getStatus, Reservation.STATUS_PENDING)));
        data.put("reservationCompleted", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery().eq(Reservation::getStatus, Reservation.STATUS_COMPLETED)));

        List<Map<String, Object>> typeDistribution = new ArrayList<>();
        String[] names = {"教室", "实验室", "图书馆", "体育馆"};
        for (int t = 1; t <= 4; t++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", names[t - 1]);
            m.put("value", resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().eq(Resource::getResourceType, t)));
            typeDistribution.add(m);
        }
        data.put("typeDistribution", typeDistribution);

        List<Reservation> recent = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .orderByDesc(Reservation::getCreatedAt)
                .last("LIMIT 5"));
        List<Long> ids = recent.stream().map(Reservation::getResourceId).distinct().toList();
        Map<Long, Resource> map = ids.isEmpty() ? Map.of()
                : resourceMapper.selectBatchIds(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(Resource::getId, r -> r));
        List<Long> userIds = recent.stream().map(Reservation::getUserId).distinct().toList();
        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));
        List<Map<String, Object>> recentList = new ArrayList<>();
        for (Reservation r : recent) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            SysUser u = userMap.get(r.getUserId());
            m.put("username", u == null ? null : u.getUsername());
            m.put("nickname", u == null ? null : u.getNickname());
            m.put("resourceId", r.getResourceId());
            Resource res = map.get(r.getResourceId());
            m.put("resourceName", res == null ? "未知资源" : res.getName());
            m.put("startTime", r.getStartTime());
            m.put("endTime", r.getEndTime());
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt());
            recentList.add(m);
        }
        data.put("recentReservations", recentList);
        return data;
    }
}

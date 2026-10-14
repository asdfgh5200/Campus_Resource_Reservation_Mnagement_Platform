package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final ResourceMapper resourceMapper;

    /** 附近可预约资源：按距离升序，用于"最近地点推荐" */
    public List<Map<String, Object>> nearby(Double lat, Double lng, Integer type, Double radius) {
        if (lat == null || lng == null) {
            return List.of();
        }
        double r = radius == null ? 1000 : radius;
        List<Resource> resources = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .eq(type != null, Resource::getResourceType, type)
                .ne(Resource::getStatus, 3));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Resource res : resources) {
            double resLat = res.getLatitude() == null ? 39.908 : res.getLatitude().doubleValue();
            double resLng = res.getLongitude() == null ? 116.397 : res.getLongitude().doubleValue();
            double distance = CheckinService.distance(lat, lng, resLat, resLng);
            if (distance > r) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", res.getId());
            item.put("name", res.getName());
            item.put("resourceType", res.getResourceType());
            item.put("resourceTypeName", typeName(res.getResourceType()));
            item.put("building", res.getBuilding());
            item.put("floor", res.getFloor());
            item.put("roomNo", res.getRoomNo());
            item.put("longitude", res.getLongitude());
            item.put("latitude", res.getLatitude());
            item.put("distance", (int) Math.round(distance));
            result.add(item);
        }
        result.sort(Comparator.comparingInt(m -> (int) m.get("distance")));
        return result;
    }

    private String typeName(Integer type) {
        return switch (type == null ? 0 : type) {
            case 1 -> "教室";
            case 2 -> "实验室";
            case 3 -> "图书馆";
            case 4 -> "体育馆";
            default -> "未知";
        };
    }
}

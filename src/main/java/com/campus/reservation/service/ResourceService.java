package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.CheckinGeofence;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.CourseSchedule;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CourseScheduleMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final CourseScheduleMapper courseScheduleMapper;
    private final MessageService messageService;

    public Page<Resource> page(Integer current, Integer size, Integer type, String keyword,
                               Integer status, String campus, String building, String floor, String roomType) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Resource> page = resourceMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<Resource>lambdaQuery()
                        .eq(type != null, Resource::getResourceType, type)
                        .eq(status != null, Resource::getStatus, status)
                        .eq(StringUtils.hasText(campus), Resource::getCampus, campus)
                        .eq(StringUtils.hasText(building), Resource::getBuilding, building)
                        .eq(StringUtils.hasText(roomType), Resource::getRoomType, roomType)
                        .eq(StringUtils.hasText(floor), Resource::getFloor, floor)
                        .and(kw != null, w -> w.like(Resource::getName, kw)
                                .or().like(Resource::getBuilding, kw)
                                .or().like(Resource::getRoomNo, kw))
                        .orderByAsc(Resource::getId));
        fillReservationInfo(page.getRecords());
        fillCourseBusy(page.getRecords());
        return page;
    }

    /**
     * 按资源类型统计各校区的楼栋清单，供前端筛选器按校区分组渲染。
     * floors / roomTypes 为该楼栋真实存在的楼层与教室类型，
     * 避免前端写死楼层与类型选项。
     */
    public List<Map<String, Object>> buildings(Integer type) {
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.select("campus", "building", "COUNT(*) AS total",
                        "GROUP_CONCAT(DISTINCT floor ORDER BY floor) AS floors",
                        "GROUP_CONCAT(DISTINCT room_type ORDER BY room_type) AS roomTypes",
                        "GROUP_CONCAT(DISTINCT resource_type ORDER BY resource_type) AS types")
                .eq(type != null, "resource_type", type)
                .isNotNull("building")
                .ne("building", "")
                .groupBy("campus", "building")
                .orderByAsc("campus")
                .orderByAsc("building");
        List<Map<String, Object>> rows = resourceMapper.selectMaps(wrapper);
        // 统一 key 命名：不同 JDBC / MyBatis 配置下 selectMaps 的别名大小写可能不一致
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("campus", pick(row, "campus"));
            item.put("building", pick(row, "building"));
            item.put("total", pick(row, "total"));
            item.put("floors", pick(row, "floors"));
            item.put("roomTypes", pick(row, "roomTypes"));
            item.put("types", pick(row, "types"));
            result.add(item);
        }
        return result;
    }

    /** 大小写不敏感地从聚合结果里取列值 */
    private static Object pick(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 标记教室当前是否正在上课（教室不参与预约，仅展示课表占用） */
    private void fillCourseBusy(List<Resource> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> roomIds = records.stream()
                .filter(r -> r.getResourceType() != null && r.getResourceType() == 1)
                .map(Resource::getId).toList();
        if (roomIds.isEmpty()) {
            return;
        }
        LocalTime nowTime = LocalTime.now();
        int dow = LocalDate.now().getDayOfWeek().getValue();
        List<CourseSchedule> courses = courseScheduleMapper.selectList(Wrappers.<CourseSchedule>lambdaQuery()
                .in(CourseSchedule::getRoomId, roomIds)
                .eq(CourseSchedule::getDayOfWeek, dow));
        for (Resource r : records) {
            if (r.getResourceType() == null || r.getResourceType() != 1) {
                continue;
            }
            r.setCourseBusy(false);
            for (CourseSchedule c : courses) {
                if (c.getRoomId().equals(r.getId())
                        && !nowTime.isBefore(c.getStartTime()) && nowTime.isBefore(c.getEndTime())) {
                    r.setCourseBusy(true);
                    r.setCourseNowName(c.getCourseName());
                    break;
                }
            }
        }
    }

    /**
     * 回填资源今日的预约信息，供前端展示与访问控制：
     * - reserved：**当前时刻**是否存在有效预约（即"当前是否有人约"），用于列表红点；
     * - reservedByMe：当前占用者是否为本人；
     * - 被他人占用时，附带对方预约的时段与备注（仅用于提示，不暴露占用者身份）。
     */
    private void fillReservationInfo(List<Resource> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(Resource::getId).toList();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getResourceId, ids)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, dayEnd)
                .gt(Reservation::getEndTime, dayStart));
        LocalDateTime now = LocalDateTime.now();
        Long myId = UserContext.get() == null ? null : UserContext.get().getUserId();
        Map<Long, List<Reservation>> byResource = list.stream()
                .collect(Collectors.groupingBy(Reservation::getResourceId));

        for (Resource r : records) {
            List<Reservation> rs = byResource.getOrDefault(r.getId(), List.of());
            // 只有"覆盖当前时刻"的预约才算当前被占用
            List<Reservation> covering = rs.stream()
                    .filter(x -> !x.getStartTime().isAfter(now) && x.getEndTime().isAfter(now))
                    .toList();
            r.setReserved(!covering.isEmpty());
            r.setReservedByMe(covering.stream()
                    .anyMatch(x -> myId != null && myId.equals(x.getUserId())));
            if (!covering.isEmpty()) {
                // 优先展示他人正在占用的信息
                Reservation pick = covering.stream()
                        .filter(x -> myId == null || !myId.equals(x.getUserId()))
                        .findFirst()
                        .orElse(covering.get(0));
                r.setReservedStart(pick.getStartTime());
                r.setReservedEnd(pick.getEndTime());
                r.setReservedRemark(pick.getRemark());
            }
            // 体育馆：统计当前时段已约数量，用于前端展示"剩余数量"
            if (r.getResourceType() != null && r.getResourceType() == 4) {
                r.setOccupiedNow(covering.stream()
                        .mapToInt(x -> x.getHeadcount() == null ? 1 : x.getHeadcount())
                        .sum());
            }
        }
    }

    public Resource getById(Long id) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw BizException.notFound("资源不存在");
        }
        return resource;
    }

    public Resource add(Resource resource) {
        if (resource.getName() == null || resource.getName().isBlank()) {
            throw BizException.badRequest("资源名称不能为空");
        }
        if (resource.getResourceType() == null || resource.getResourceType() < 1 || resource.getResourceType() > 4) {
            throw BizException.badRequest("资源类型不合法");
        }
        resource.setId(null);
        if (resource.getStatus() == null) {
            resource.setStatus(1);
        }
        if (resource.getOpenTime() == null) {
            resource.setOpenTime(LocalTime.of(8, 0));
        }
        if (resource.getCloseTime() == null) {
            resource.setCloseTime(LocalTime.of(22, 0));
        }
        if (resource.getMaxDuration() == null) {
            resource.setMaxDuration(120);
        }
        // 新增资源未提供坐标时按类型给一个兜底值，管理员可在资源档案里用「取当前位置」校准
        if (resource.getLatitude() == null || resource.getLongitude() == null) {
            CheckinGeofence.Point point = CheckinGeofence.of(resource.getResourceType());
            resource.setLatitude(BigDecimal.valueOf(point.latitude()).setScale(6, RoundingMode.HALF_UP));
            resource.setLongitude(BigDecimal.valueOf(point.longitude()).setScale(6, RoundingMode.HALF_UP));
        }
        resourceMapper.insert(resource);
        return resource;
    }

    /**
     * 更新资源。打卡坐标按「校区 + 楼栋」共享：
     * 管理员校准某一资源的坐标后，同校区同楼栋的其他资源一并更新，避免同一楼栋两套坐标导致打卡误判。
     */
    @Transactional
    public Resource update(Resource resource) {
        if (resource.getId() == null) {
            throw BizException.badRequest("资源 ID 不能为空");
        }
        Resource before = getById(resource.getId());
        resourceMapper.updateById(resource);
        Resource after = getById(resource.getId());
        syncBuildingCheckinPoint(before, after);
        // 管理员修改已存在预约的资源时，向预约人推送站内通知
        notifyActiveReservationUsers(before, after, false);
        return after;
    }

    /** 同校区同楼栋共享打卡坐标：坐标发生变更时批量同步，未传坐标则不动 */
    private void syncBuildingCheckinPoint(Resource before, Resource after) {
        if (after.getLatitude() == null || after.getLongitude() == null) {
            return;
        }
        if (after.getCampus() == null || after.getBuilding() == null) {
            return;
        }
        boolean same = java.util.Objects.equals(before.getLatitude(), after.getLatitude())
                && java.util.Objects.equals(before.getLongitude(), after.getLongitude());
        if (same) {
            return;
        }
        Resource patch = new Resource();
        patch.setLatitude(after.getLatitude());
        patch.setLongitude(after.getLongitude());
        resourceMapper.update(patch, Wrappers.<Resource>lambdaUpdate()
                .eq(Resource::getCampus, after.getCampus())
                .eq(Resource::getBuilding, after.getBuilding())
                .ne(Resource::getId, after.getId()));
    }

    public void delete(Long id) {
        getById(id);
        resourceMapper.deleteById(id);
    }

    @Transactional
    public Resource changeStatus(Long id, Integer status) {
        if (status == null || status < Resource.STATUS_AVAILABLE || status > Resource.STATUS_REPORTED) {
            throw BizException.badRequest("状态值不合法");
        }
        Resource before = getById(id);
        Resource update = new Resource();
        update.setId(id);
        update.setStatus(status);
        resourceMapper.updateById(update);
        Resource after = getById(id);
        notifyActiveReservationUsers(before, after, true);
        return after;
    }

    /**
     * 当资源信息或状态发生变化时，向当前及未来进行中预约的用户推送站内消息。
     * 普通预约用户看到提示后应及时联系管理员。
     */
    private void notifyActiveReservationUsers(Resource before, Resource after, boolean statusOnly) {
        if (after == null || after.getId() == null) return;
        boolean statusChanged = !java.util.Objects.equals(before.getStatus(), after.getStatus());
        boolean timeChanged = !java.util.Objects.equals(before.getOpenTime(), after.getOpenTime())
                || !java.util.Objects.equals(before.getCloseTime(), after.getCloseTime());
        boolean nameChanged = !java.util.Objects.equals(before.getName(), after.getName());
        if (statusOnly && !statusChanged) return;
        if (!statusChanged && !timeChanged && !nameChanged) return;

        StringBuilder tip = new StringBuilder();
        tip.append("平台已调整资源「").append(after.getName()).append("」");
        if (statusChanged) {
            tip.append("状态变更：").append(statusLabel(before.getStatus()))
                    .append(" → ").append(statusLabel(after.getStatus())).append("；");
        }
        if (timeChanged) {
            tip.append("开放时间：").append(before.getOpenTime()).append("-").append(before.getCloseTime())
                    .append(" → ").append(after.getOpenTime()).append("-").append(after.getCloseTime()).append("；");
        }
        if (nameChanged) {
            tip.append("名称：").append(before.getName()).append(" → ").append(after.getName()).append("；");
        }
        tip.append("如对您的预约造成影响，请通过「意见反馈」与我们联系。");
        String content = tip.toString();

        LocalDateTime now = LocalDateTime.now();
        List<Reservation> affected = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getResourceId, after.getId())
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .ge(Reservation::getEndTime, now));
        for (Reservation r : affected) {
            messageService.notifyUser(r.getUserId(), content);
        }
    }

    private static String statusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case Resource.STATUS_AVAILABLE -> "可预约";
            case Resource.STATUS_IN_USE -> "使用中";
            case Resource.STATUS_REPAIRING -> "维修中";
            case Resource.STATUS_REPORTED -> "报修中";
            default -> "未知";
        };
    }

    public List<Resource> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return resourceMapper.selectBatchIds(ids);
    }
}

package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.CourseSchedule;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CourseScheduleMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final CourseScheduleMapper courseScheduleMapper;

    public Page<Resource> page(Integer current, Integer size, Integer type, String keyword,
                               Integer status, String building, String floor) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Resource> page = resourceMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<Resource>lambdaQuery()
                        .eq(type != null, Resource::getResourceType, type)
                        .eq(status != null, Resource::getStatus, status)
                        .eq(StringUtils.hasText(building), Resource::getBuilding, building)
                        .eq(StringUtils.hasText(floor), Resource::getFloor, floor)
                        .and(kw != null, w -> w.like(Resource::getName, kw)
                                .or().like(Resource::getBuilding, kw)
                                .or().like(Resource::getRoomNo, kw))
                        .orderByAsc(Resource::getId));
        fillReservationInfo(page.getRecords());
        fillCourseBusy(page.getRecords());
        return page;
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
        resourceMapper.insert(resource);
        return resource;
    }

    public Resource update(Resource resource) {
        if (resource.getId() == null) {
            throw BizException.badRequest("资源 ID 不能为空");
        }
        getById(resource.getId());
        resourceMapper.updateById(resource);
        return getById(resource.getId());
    }

    public void delete(Long id) {
        getById(id);
        resourceMapper.deleteById(id);
    }

    public Resource changeStatus(Long id, Integer status) {
        if (status == null || status < 1 || status > 3) {
            throw BizException.badRequest("状态值不合法（1 可预约 / 2 使用中 / 3 维修中）");
        }
        getById(id);
        Resource update = new Resource();
        update.setId(id);
        update.setStatus(status);
        resourceMapper.updateById(update);
        return getById(id);
    }

    public List<Resource> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return resourceMapper.selectBatchIds(ids);
    }
}

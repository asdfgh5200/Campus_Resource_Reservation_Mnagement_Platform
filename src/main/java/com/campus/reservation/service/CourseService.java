package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.BizException;
import com.campus.reservation.dto.CourseImportDTO;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourseService {

    /** 未指定结束时，默认按此时长查找可用教室（分钟） */
    private static final int DEFAULT_FREE_ROOM_SPAN_MINUTES = 120;
    /** 资源维修中状态 */
    private static final int RESOURCE_STATUS_REPAIR = 3;
    /** 资源报修中状态（已提交报修、等待确认） */
    private static final int RESOURCE_STATUS_REPORTED = 4;

    private final CourseScheduleMapper courseScheduleMapper;
    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;

    public int importCourses(List<CourseImportDTO> list) {
        if (list == null || list.isEmpty()) {
            throw BizException.badRequest("导入数据不能为空");
        }
        int count = 0;
        for (CourseImportDTO dto : list) {
            if (dto.getRoomId() == null || dto.getCourseName() == null || dto.getCourseName().isBlank()
                    || dto.getDayOfWeek() == null || dto.getDayOfWeek() < 1 || dto.getDayOfWeek() > 7
                    || dto.getStartTime() == null || dto.getEndTime() == null) {
                throw BizException.badRequest("课表数据不完整，请检查教室、课程名称、星期与起止时间");
            }
            if (!dto.getStartTime().isBefore(dto.getEndTime())) {
                throw BizException.badRequest("课程结束时间必须晚于开始时间：" + dto.getCourseName());
            }
            CourseSchedule course = new CourseSchedule();
            course.setRoomId(dto.getRoomId());
            course.setCourseName(dto.getCourseName());
            course.setDayOfWeek(dto.getDayOfWeek());
            course.setStartTime(dto.getStartTime());
            course.setEndTime(dto.getEndTime());
            course.setTeacher(dto.getTeacher());
            course.setTerm(dto.getTerm());
            courseScheduleMapper.insert(course);
            count++;
        }
        return count;
    }

    /** 查询教室当日可预约时段：开放时间 - 课程占用 - 已有预约 */
    public Map<String, Object> available(Long roomId, LocalDate date) {
        Resource room = resourceMapper.selectById(roomId);
        if (room == null) {
            throw BizException.notFound("教室不存在");
        }
        if (date == null) {
            date = LocalDate.now();
        }
        int dayOfWeek = date.getDayOfWeek().getValue();
        List<CourseSchedule> courses = courseScheduleMapper.selectList(Wrappers.<CourseSchedule>lambdaQuery()
                .eq(CourseSchedule::getRoomId, roomId)
                .eq(CourseSchedule::getDayOfWeek, dayOfWeek));

        // 当日已有有效预约同样视为占用
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<Reservation> reservations = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getResourceId, roomId)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .ge(Reservation::getStartTime, dayStart)
                .lt(Reservation::getEndTime, dayEnd));

        List<int[]> busy = new ArrayList<>();
        for (CourseSchedule c : courses) {
            busy.add(new int[]{c.getStartTime().toSecondOfDay(), c.getEndTime().toSecondOfDay()});
        }
        for (Reservation r : reservations) {
            int s = r.getStartTime().toLocalTime().toSecondOfDay();
            int e = r.getEndTime().toLocalTime().toSecondOfDay();
            busy.add(new int[]{Math.max(s, room.getOpenTime().toSecondOfDay()), Math.min(e, room.getCloseTime().toSecondOfDay())});
        }
        busy.sort(Comparator.comparingInt(a -> a[0]));

        int open = room.getOpenTime().toSecondOfDay();
        int close = room.getCloseTime().toSecondOfDay();
        List<Map<String, Object>> freeSlots = new ArrayList<>();
        int cur = open;
        for (int[] interval : busy) {
            if (interval[0] > cur) {
                freeSlots.add(slot(cur, Math.min(interval[0], close)));
            }
            cur = Math.max(cur, interval[1]);
            if (cur >= close) {
                break;
            }
        }
        if (cur < close) {
            freeSlots.add(slot(cur, close));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomId", roomId);
        data.put("roomName", room.getName());
        data.put("date", date);
        data.put("openTime", room.getOpenTime());
        data.put("closeTime", room.getCloseTime());
        data.put("courses", courses);
        data.put("freeSlots", freeSlots);
        return data;
    }

    /**
     * 按日期与时段批量筛选可选教室：一次查完课程与预约后在内存比对，避免逐教室查询。
     * 只返回完整覆盖所选时段、且未被课程或预约占用、非维修/报修中的教室。
     *
     * @param buildings 楼栋列表，为空表示不限楼栋
     * @param campus    校区，为空表示不限校区
     * @param roomTypes 教室类型，为空表示不限类型
     * @param keyword   房间号或名称关键词，为空表示不搜索
     */
    public List<Map<String, Object>> freeRooms(LocalDate date, LocalTime start, LocalTime end,
                                               List<String> buildings, String floor) {
        return freeRooms(date, start, end, buildings, floor, null, null, null);
    }

    /** 带校区限定的空闲教室查询，供校区/楼栋二级筛选使用 */
    public List<Map<String, Object>> freeRooms(LocalDate date, LocalTime start, LocalTime end,
                                               List<String> buildings, String floor, String campus) {
        return freeRooms(date, start, end, buildings, floor, campus, null, null);
    }

    /** 带校区、教室类型限定的空闲教室查询，供三级筛选使用 */
    public List<Map<String, Object>> freeRooms(LocalDate date, LocalTime start, LocalTime end,
                                               List<String> buildings, String floor, String campus,
                                               List<String> roomTypes) {
        return freeRooms(date, start, end, buildings, floor, campus, roomTypes, null);
    }

    /** 带校区、教室类型与关键词的空闲教室查询 */
    public List<Map<String, Object>> freeRooms(LocalDate date, LocalTime start, LocalTime end,
                                               List<String> buildings, String floor, String campus,
                                               List<String> roomTypes, String keyword) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalTime from = start == null ? roundUpToHalfHour(LocalTime.now()) : start;
        LocalTime to = end == null ? from.plusMinutes(DEFAULT_FREE_ROOM_SPAN_MINUTES) : end;
        if (!from.isBefore(to)) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        int fromSec = from.toSecondOfDay();
        int toSec = to.toSecondOfDay();

        List<String> wantedBuildings = buildings == null ? List.of()
                : buildings.stream().filter(StringUtils::hasText).map(String::trim).toList();
        List<String> wantedTypes = roomTypes == null ? List.of()
                : roomTypes.stream().filter(StringUtils::hasText).map(String::trim).toList();
        List<Resource> rooms = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .eq(Resource::getResourceType, 1)
                .notIn(Resource::getStatus, RESOURCE_STATUS_REPAIR, RESOURCE_STATUS_REPORTED)
                .eq(StringUtils.hasText(campus), Resource::getCampus, campus)
                .in(!wantedBuildings.isEmpty(), Resource::getBuilding, wantedBuildings)
                .in(!wantedTypes.isEmpty(), Resource::getRoomType, wantedTypes)
                .eq(StringUtils.hasText(floor), Resource::getFloor, floor)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Resource::getRoomNo, keyword.trim())
                        .or().like(Resource::getName, keyword.trim()))
                .orderByAsc(Resource::getCampus)
                .orderByAsc(Resource::getBuilding)
                .orderByAsc(Resource::getFloor)
                .orderByAsc(Resource::getRoomNo));
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(Resource::getId).toList();
        // 占用区间：星期匹配的课表 + 当日该时段内仍然有效的预约
        Map<Long, List<int[]>> busyByRoom = new HashMap<>();
        for (CourseSchedule c : courseScheduleMapper.selectList(Wrappers.<CourseSchedule>lambdaQuery()
                .in(CourseSchedule::getRoomId, roomIds)
                .eq(CourseSchedule::getDayOfWeek, day.getDayOfWeek().getValue()))) {
            busyByRoom.computeIfAbsent(c.getRoomId(), k -> new ArrayList<>())
                    .add(new int[]{c.getStartTime().toSecondOfDay(), c.getEndTime().toSecondOfDay()});
        }
        for (Reservation r : reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getResourceId, roomIds)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, day.atTime(to))
                .gt(Reservation::getEndTime, day.atTime(from)))) {
            busyByRoom.computeIfAbsent(r.getResourceId(), k -> new ArrayList<>())
                    .add(new int[]{r.getStartTime().toLocalTime().toSecondOfDay(),
                            r.getEndTime().toLocalTime().toSecondOfDay()});
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Resource room : rooms) {
            int open = room.getOpenTime().toSecondOfDay();
            int close = room.getCloseTime().toSecondOfDay();
            // 必须完整覆盖所选时段，避免用到一半被迫离场
            if (fromSec < open || toSec > close) {
                continue;
            }
            List<int[]> busy = busyByRoom.getOrDefault(room.getId(), List.of());
            if (busy.stream().anyMatch(x -> x[0] < toSec && x[1] > fromSec)) {
                continue;
            }
            int freeUntil = close;
            for (int[] x : busy) {
                if (x[0] >= toSec && x[0] < freeUntil) {
                    freeUntil = x[0];
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", room.getId());
            item.put("name", room.getName());
            item.put("campus", room.getCampus());
            item.put("building", room.getBuilding());
            item.put("floor", room.getFloor());
            item.put("roomType", room.getRoomType());
            item.put("roomNo", room.getRoomNo());
            item.put("capacity", room.getCapacity());
            item.put("freeUntil", toLocalTime(freeUntil));
            result.add(item);
        }
        return result;
    }

    /** 向上取整到下一个整点或半点，作为默认起始时间 */
    private LocalTime roundUpToHalfHour(LocalTime now) {
        if (now.getMinute() == 0 && now.getSecond() == 0) {
            return now;
        }
        return now.getMinute() < 30
                ? now.withMinute(30).withSecond(0).withNano(0)
                : now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
    }

    private LocalTime toLocalTime(int secondOfDay) {
        return LocalTime.of(secondOfDay / 3600, secondOfDay % 3600 / 60, secondOfDay % 60);
    }

    /** 课表列表（管理端）：可按教室、星期、校区或楼栋筛选，附带教室名称 */
    public List<Map<String, Object>> list(Long roomId, Integer dayOfWeek, String building, String campus) {
        List<CourseSchedule> courses = courseScheduleMapper.selectList(Wrappers.<CourseSchedule>lambdaQuery()
                .eq(roomId != null, CourseSchedule::getRoomId, roomId)
                .eq(dayOfWeek != null, CourseSchedule::getDayOfWeek, dayOfWeek)
                .orderByAsc(CourseSchedule::getDayOfWeek)
                .orderByAsc(CourseSchedule::getStartTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseSchedule c : courses) {
            Resource room = resourceMapper.selectById(c.getRoomId());
            if (room == null) {
                continue;
            }
            if (StringUtils.hasText(building) && !building.equals(room.getBuilding())) {
                continue;
            }
            if (StringUtils.hasText(campus) && !campus.equals(room.getCampus())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("roomId", c.getRoomId());
            m.put("roomName", room.getName());
            m.put("campus", room.getCampus());
            m.put("building", room.getBuilding());
            m.put("floor", room.getFloor());
            m.put("courseName", c.getCourseName());
            m.put("dayOfWeek", c.getDayOfWeek());
            m.put("startTime", c.getStartTime());
            m.put("endTime", c.getEndTime());
            m.put("teacher", c.getTeacher());
            m.put("term", c.getTerm());
            result.add(m);
        }
        return result;
    }

    /** 删除一条课表记录 */
    public void delete(Long id) {
        if (id == null || courseScheduleMapper.selectById(id) == null) {
            throw BizException.notFound("课表记录不存在");
        }
        courseScheduleMapper.deleteById(id);
    }

    private Map<String, Object> slot(int startSec, int endSec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", LocalTime.of(startSec / 3600, startSec % 3600 / 60, startSec % 60));
        m.put("end", LocalTime.of(endSec / 3600, endSec % 3600 / 60, endSec % 60));
        return m;
    }
}

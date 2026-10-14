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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourseService {

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
                throw BizException.badRequest("课表数据不完整：" + dto);
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

    /** 课表列表（管理端）：可按教室、星期筛选，附带教室名称 */
    public List<Map<String, Object>> list(Long roomId, Integer dayOfWeek, String building) {
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("roomId", c.getRoomId());
            m.put("roomName", room.getName());
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

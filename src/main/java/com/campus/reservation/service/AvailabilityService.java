package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.TermCalendar;
import com.campus.reservation.entity.CampusClosure;
import com.campus.reservation.entity.CourseSchedule;
import com.campus.reservation.entity.Holiday;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CourseScheduleMapper;
import com.campus.reservation.mapper.HolidayMapper;
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

/**
 * 资源可用性判定：一个资源在某天某时段到底能不能用。
 *
 * <p>四类资源共用同一套口径，避免「列表页说能用、下单被拒」这类口径分裂：
 * <ol>
 *   <li>校园一键闭馆 / 定向闭馆窗口 / 节假日；</li>
 *   <li>资源自身：维修中、报修中、开放时间是否完整覆盖所选时段；</li>
 *   <li>课表：按教学周位图过滤，只算该周真正开课的课程；</li>
 *   <li>预约：体育馆按 headcount 之和算剩余，其余资源时段独占；</li>
 *   <li>可提前预约的日期范围：实验室/体育馆 7 天、图书馆次日。</li>
 * </ol>
 *
 * <p>列表页的「查空闲」、资源详情的时段条、智约机器人推荐方案都走这里。
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    /** 未指定结束时刻时，默认按此时长查找可用资源（分钟） */
    public static final int DEFAULT_SPAN_MINUTES = 120;
    /** 实验室(2)/体育馆(4)：仅可预约未来一周内的时间（含第 7 天） */
    public static final int LAB_GYM_ADVANCE_DAYS = 7;
    /** 图书馆(3)：最多只能预约到次日 */
    public static final int LIBRARY_ADVANCE_DAYS = 1;

    /** 可参与可用性判定的资源类型：教室、实验室、图书馆座位、体育馆 */
    private static final List<Integer> BOOKABLE_TYPES = List.of(1, 2, 3, 4);

    private final ResourceMapper resourceMapper;
    private final CourseScheduleMapper courseScheduleMapper;
    private final ReservationMapper reservationMapper;
    private final HolidayMapper holidayMapper;
    private final ClosureService closureService;
    private final SystemConfigService systemConfigService;

    /**
     * 判定结论：available=false 时 reason 说明原因；
     * remaining 为可容纳的剩余数量（体育馆单位取 resource.unit，其余资源可用即 1）。
     */
    public record Availability(boolean available, String reason, int remaining, LocalTime freeUntil) {
        public static Availability free(int remaining, LocalTime freeUntil) {
            return new Availability(true, null, remaining, freeUntil);
        }

        public static Availability blocked(String reason) {
            return new Availability(false, reason, 0, null);
        }
    }

    /**
     * 按日期与时段批量筛选可用资源。
     *
     * @param type      资源类型，为空表示四类都要
     * @param campus    校区，为空不限
     * @param buildings 楼栋，为空不限
     * @param roomTypes 细分类型（教室类型/实验方向/运动类型），为空不限
     * @param floor     楼层，为空不限
     * @param keyword   房间号或名称关键词，为空不搜索
     */
    public List<Map<String, Object>> freeResources(LocalDate date, LocalTime start, LocalTime end, Integer type,
                                                   String campus, List<String> buildings, List<String> roomTypes,
                                                   String floor, String keyword) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalTime from = start == null ? roundUpToHalfHour(LocalTime.now()) : start;
        LocalTime to = end == null ? from.plusMinutes(DEFAULT_SPAN_MINUTES) : end;
        if (!from.isBefore(to)) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        checkAdvanceDays(type, day);
        String dayReason = dayBlockReason(day);
        if (dayReason != null) {
            // 闭馆或节假日：整批都不可用，直接把原因抛给调用方，避免前端只看到一片空白
            throw BizException.badRequest(dayReason);
        }
        int fromSec = from.toSecondOfDay();
        int toSec = to.toSecondOfDay();

        List<String> wantedBuildings = cleanList(buildings);
        List<String> wantedTypes = cleanList(roomTypes);
        List<Resource> candidates = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .in(Resource::getResourceType, type == null ? BOOKABLE_TYPES : List.of(type))
                .notIn(Resource::getStatus, Resource.STATUS_REPAIRING, Resource.STATUS_REPORTED)
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
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> ids = candidates.stream().map(Resource::getId).toList();
        Map<Long, List<CourseSchedule>> courses = loadCourses(ids, day);
        Map<Long, List<Reservation>> reservations = loadReservations(ids, day);
        List<CampusClosure> closures = closureService.listActive();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Resource r : candidates) {
            Availability a = evaluate(r, fromSec, toSec,
                    courses.getOrDefault(r.getId(), List.of()),
                    reservations.getOrDefault(r.getId(), List.of()),
                    closureWindows(r, day, closures));
            if (!a.available()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("name", r.getName());
            item.put("resourceType", r.getResourceType());
            item.put("campus", r.getCampus());
            item.put("building", r.getBuilding());
            item.put("floor", r.getFloor());
            item.put("roomType", r.getRoomType());
            item.put("roomNo", r.getRoomNo());
            item.put("capacity", r.getCapacity());
            item.put("unit", r.getUnit());
            item.put("openTime", r.getOpenTime());
            item.put("closeTime", r.getCloseTime());
            item.put("remaining", a.remaining());
            item.put("freeUntil", a.freeUntil());
            result.add(item);
        }
        return result;
    }

    /**
     * 单个资源在具体时段的可用性判定（预约前预检、机器人推荐前自检都走这里）。
     * 入参与 {@code ReservationService#create} 的校验口径一致，但只做判定、不写库。
     */
    public Availability check(Resource resource, LocalDateTime start, LocalDateTime end) {
        if (resource == null) {
            return Availability.blocked("资源不存在");
        }
        if (start == null || end == null || !start.isBefore(end)) {
            return Availability.blocked("时间区间不合法");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return Availability.blocked("暂不支持跨天预约");
        }
        String dayReason = dayBlockReason(start.toLocalDate());
        if (dayReason != null) {
            return Availability.blocked(dayReason);
        }
        List<CourseSchedule> courses = loadCourses(List.of(resource.getId()), start.toLocalDate())
                .getOrDefault(resource.getId(), List.of());
        List<Reservation> reservations = loadReservations(List.of(resource.getId()), start.toLocalDate())
                .getOrDefault(resource.getId(), List.of());
        return evaluate(resource, start.toLocalTime().toSecondOfDay(), end.toLocalTime().toSecondOfDay(),
                courses, reservations, closureWindows(resource, start.toLocalDate(), closureService.listActive()));
    }

    /**
     * 某资源某天的完整排期：开放时间、是否闭馆/节假日、课程与预约占用、以及剩余可约窗口。
     * 机器人回答「XX 哪天有空」与各端展示占用都用这一份口径，不再各自拼规则。
     */
    public Map<String, Object> daySchedule(Resource resource, LocalDate day) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resourceId", resource.getId());
        m.put("resourceName", resource.getName());
        m.put("resourceType", resource.getResourceType());
        m.put("date", day.toString());
        m.put("week", TermCalendar.weekOf(day));
        String dayReason = dayBlockReason(day);
        if (dayReason != null) {
            m.put("closed", true);
            m.put("closedReason", dayReason);
            m.put("busySlots", List.of());
            m.put("freeSlots", List.of());
            return m;
        }
        m.put("closed", false);
        LocalTime open = resource.getOpenTime();
        LocalTime close = resource.getCloseTime();
        m.put("openTime", open == null ? null : open.toString());
        m.put("closeTime", close == null ? null : close.toString());
        if (open == null || close == null) {
            m.put("note", "该资源未设置开放时间，无法判断可用时段");
            m.put("busySlots", List.of());
            m.put("freeSlots", List.of());
            return m;
        }

        List<Map<String, Object>> busy = new ArrayList<>();
        for (CourseSchedule c : loadCourses(List.of(resource.getId()), day)
                .getOrDefault(resource.getId(), List.of())) {
            busy.add(slot("course", "上课：" + c.getCourseName(), c.getStartTime(), c.getEndTime(), null));
        }
        for (Reservation r : loadReservations(List.of(resource.getId()), day)
                .getOrDefault(resource.getId(), List.of())) {
            busy.add(slot("reservation", "已被预约", r.getStartTime().toLocalTime(),
                    r.getEndTime().toLocalTime(), r.getHeadcount()));
        }
        for (int[] w : closureWindows(resource, day, closureService.listActive())) {
            busy.add(slot("closure", "闭馆", toLocalTime(w[0]), toLocalTime(w[1]), null));
        }
        busy.sort(Comparator.comparing(x -> String.valueOf(x.get("start"))));
        m.put("busySlots", busy);
        m.put("freeSlots", freeWindows(open, close, busy));
        if (resource.getResourceType() != null && resource.getResourceType() == 4) {
            m.put("capacity", resource.getCapacity());
            m.put("unit", resource.getUnit());
            m.put("note", "体育馆可多人同时预约：busySlots 里 headcount 之和不得超过 capacity，未超即可继续下单");
        }
        return m;
    }

    /** 占用条目：kind 区分课程/预约/闭馆，方便模型与前端解释「为什么不能用」 */
    private static Map<String, Object> slot(String kind, String label, LocalTime start, LocalTime end, Integer headcount) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", kind);
        s.put("label", label);
        s.put("start", String.format("%02d:%02d", start.getHour(), start.getMinute()));
        s.put("end", String.format("%02d:%02d", end.getHour(), end.getMinute()));
        if (headcount != null) {
            s.put("headcount", headcount);
        }
        return s;
    }

    /** 开放时段内去掉占用后的空闲窗口，形如 08:00-10:45 */
    private static List<String> freeWindows(LocalTime open, LocalTime close, List<Map<String, Object>> busy) {
        List<int[]> blocks = new ArrayList<>();
        for (Map<String, Object> b : busy) {
            int s = Math.max(LocalTime.parse(String.valueOf(b.get("start"))).toSecondOfDay(), open.toSecondOfDay());
            int e = Math.min(LocalTime.parse(String.valueOf(b.get("end"))).toSecondOfDay(), close.toSecondOfDay());
            if (s < e) {
                blocks.add(new int[]{s, e});
            }
        }
        blocks.sort(Comparator.comparingInt(b -> b[0]));
        List<String> free = new ArrayList<>();
        int cursor = open.toSecondOfDay();
        int end = close.toSecondOfDay();
        for (int[] b : blocks) {
            if (b[0] > cursor) {
                free.add(hm(cursor) + "-" + hm(b[0]));
            }
            cursor = Math.max(cursor, b[1]);
        }
        if (cursor < end) {
            free.add(hm(cursor) + "-" + hm(end));
        }
        return free;
    }

    private static String hm(int secondOfDay) {
        return String.format("%02d:%02d", secondOfDay / 3600, secondOfDay % 3600 / 60);
    }

    /** 该日期是否整体不可用（闭馆 / 节假日），可用时返回 null */
    public String dayBlockReason(LocalDate day) {
        if (systemConfigService.isClosed()) {
            return "校园当前处于闭馆状态，暂停开放预约";
        }
        Long holidays = holidayMapper.selectCount(Wrappers.<Holiday>lambdaQuery()
                .le(Holiday::getStartDate, day)
                .ge(Holiday::getEndDate, day));
        if (holidays != null && holidays > 0) {
            return "节假日暂停预约";
        }
        return null;
    }

    /** 该类型最多能提前几天预约，教室不限制 */
    public static int advanceDaysOf(Integer type) {
        if (type == null) return LAB_GYM_ADVANCE_DAYS;
        return switch (type) {
            case 2, 4 -> LAB_GYM_ADVANCE_DAYS;
            case 3 -> LIBRARY_ADVANCE_DAYS;
            default -> Integer.MAX_VALUE;
        };
    }

    private void checkAdvanceDays(Integer type, LocalDate day) {
        int limit = advanceDaysOf(type);
        if (limit == Integer.MAX_VALUE) {
            return;
        }
        if (day.isAfter(LocalDate.now().plusDays(limit))) {
            String what = type != null && type == 3 ? "图书馆最多只能预约到次日"
                    : "实验室、体育馆仅可预约未来一周内的时间";
            throw BizException.badRequest(what + "，请换个日期");
        }
    }

    /** 核心判定：入参为已按日期预筛好的课程与预约，避免逐资源查库 */
    private Availability evaluate(Resource r, int fromSec, int toSec,
                                  List<CourseSchedule> dayCourses, List<Reservation> dayReservations,
                                  List<int[]> closureWindows) {
        if (r.getStatus() != null && r.getStatus() == Resource.STATUS_REPAIRING) {
            return Availability.blocked("维修中");
        }
        if (r.getStatus() != null && r.getStatus() == Resource.STATUS_REPORTED) {
            return Availability.blocked("报修中");
        }
        LocalTime openTime = r.getOpenTime();
        LocalTime closeTime = r.getCloseTime();
        if (openTime == null || closeTime == null) {
            return Availability.blocked("资源未设置开放时间");
        }
        int open = openTime.toSecondOfDay();
        int close = closeTime.toSecondOfDay();
        // 必须完整覆盖所选时段，否则用户会用到一半被迫离场
        if (fromSec < open || toSec > close) {
            return Availability.blocked("所选时段超出开放时间（" + openTime + " - " + closeTime + "）");
        }
        if (closedAt(fromSec, toSec, closureWindows)) {
            return Availability.blocked("该时段闭馆");
        }

        int freeUntil = close;
        for (CourseSchedule c : dayCourses) {
            if (c.getStartTime() == null || c.getEndTime() == null) {
                continue;
            }
            int s = c.getStartTime().toSecondOfDay();
            int e = c.getEndTime().toSecondOfDay();
            if (s < toSec && e > fromSec) {
                return Availability.blocked(String.format("该时段「%s」上课（%s - %s）",
                        c.getCourseName(), c.getStartTime(), c.getEndTime()));
            }
            if (s >= toSec && s < freeUntil) {
                freeUntil = s;
            }
        }

        int occupied = 0;
        boolean clash = false;
        for (Reservation res : dayReservations) {
            int s = res.getStartTime().toLocalTime().toSecondOfDay();
            int e = res.getEndTime().toLocalTime().toSecondOfDay();
            if (s < toSec && e > fromSec) {
                clash = true;
                occupied += res.getHeadcount() == null ? 1 : res.getHeadcount();
            }
            if (s >= toSec && s < freeUntil) {
                freeUntil = s;
            }
        }

        boolean isGym = r.getResourceType() != null && r.getResourceType() == 4;
        if (isGym) {
            int capacity = r.getCapacity() == null ? 0 : r.getCapacity();
            int remaining = Math.max(0, capacity - occupied);
            if (remaining <= 0) {
                return Availability.blocked("该时段已约满");
            }
            return Availability.free(remaining, toLocalTime(freeUntil));
        }
        if (clash) {
            return Availability.blocked("该时段已被预约");
        }
        return Availability.free(1, toLocalTime(freeUntil));
    }

    /** 与 {@link ClosureService#isClosedAt} 同一口径：整类闭馆命中该类型全部资源，定向闭馆只命中指定资源 */
    private List<int[]> closureWindows(Resource r, LocalDate day, List<CampusClosure> closures) {
        if (closures == null || closures.isEmpty()) {
            return List.of();
        }
        List<int[]> windows = new ArrayList<>();
        for (CampusClosure c : closures) {
            if (c.getResourceType() == null || !c.getResourceType().equals(r.getResourceType())) {
                continue;
            }
            if (c.getResourceId() != null && !c.getResourceId().equals(r.getId())) {
                continue;
            }
            if (c.getClosureDate() == null || !c.getClosureDate().equals(day)
                    || c.getStartTime() == null || c.getEndTime() == null) {
                continue;
            }
            windows.add(new int[]{c.getStartTime().toSecondOfDay(), c.getEndTime().toSecondOfDay()});
        }
        return windows;
    }

    private static boolean closedAt(int fromSec, int toSec, List<int[]> windows) {
        for (int[] w : windows) {
            if (w[0] < toSec && w[1] > fromSec) {
                return true;
            }
        }
        return false;
    }

    /** 当天与资源相关的课程，按教学周位图过滤掉这周不上课的行 */
    private Map<Long, List<CourseSchedule>> loadCourses(List<Long> ids, LocalDate day) {
        Map<Long, List<CourseSchedule>> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        int week = TermCalendar.weekOf(day);
        for (CourseSchedule c : courseScheduleMapper.selectList(Wrappers.<CourseSchedule>lambdaQuery()
                .in(CourseSchedule::getRoomId, ids)
                .eq(CourseSchedule::getDayOfWeek, day.getDayOfWeek().getValue()))) {
            if (!TermCalendar.occupies(c.getWeeks(), week) || c.getStartTime() == null || c.getEndTime() == null) {
                continue;
            }
            map.computeIfAbsent(c.getRoomId(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    /** 当天仍然有效的预约（待打卡/使用中） */
    private Map<Long, List<Reservation>> loadReservations(List<Long> ids, LocalDate day) {
        Map<Long, List<Reservation>> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        for (Reservation r : reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getResourceId, ids)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, dayEnd)
                .gt(Reservation::getEndTime, dayStart))) {
            map.computeIfAbsent(r.getResourceId(), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    /** 向上取整到下一个整点或半点，作为默认起始时间 */
    private static LocalTime roundUpToHalfHour(LocalTime now) {
        if (now.getMinute() == 0 && now.getSecond() == 0) {
            return now;
        }
        return now.getMinute() < 30
                ? now.withMinute(30).withSecond(0).withNano(0)
                : now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
    }

    private static LocalTime toLocalTime(int secondOfDay) {
        return LocalTime.of(secondOfDay / 3600, secondOfDay % 3600 / 60, secondOfDay % 60);
    }
}

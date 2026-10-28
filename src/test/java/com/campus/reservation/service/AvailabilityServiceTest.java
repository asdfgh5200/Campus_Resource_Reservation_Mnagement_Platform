package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.entity.CourseSchedule;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CourseScheduleMapper;
import com.campus.reservation.mapper.HolidayMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AvailabilityService 单元测试：列表页「查空闲」与智约机器人共用的一套可用性口径。
 * 覆盖课程/预约/周次位图/闭馆/开放时间/提前天数/体育馆容量。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityServiceTest {

    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private CourseScheduleMapper courseScheduleMapper;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private HolidayMapper holidayMapper;
    @Mock
    private ClosureService closureService;
    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private AvailabilityService service;

    /** 2026-09-07 为教学第 1 周周一，本日属于第 7 周 */
    private static final LocalDate MON = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void baseStubs() {
        when(systemConfigService.isClosed()).thenReturn(false);
        when(closureService.listActive()).thenReturn(List.of());
        when(holidayMapper.selectCount(any())).thenReturn(0L);
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectList(any())).thenReturn(List.of());
    }

    private Resource resource(long id, int type, int capacity) {
        Resource r = new Resource();
        r.setId(id);
        r.setName("资源" + id);
        r.setResourceType(type);
        r.setCapacity(capacity);
        r.setUnit(type == 4 ? "人" : null);
        r.setStatus(Resource.STATUS_AVAILABLE);
        r.setOpenTime(type == 4 ? LocalTime.of(9, 0) : LocalTime.of(8, 0));
        r.setCloseTime(type == 4 ? LocalTime.of(21, 0) : LocalTime.of(22, 0));
        return r;
    }

    /** 生成 30 位周次位图：给定周次置 1，其余为 0 */
    private static String weeks(Integer... numbers) {
        char[] bits = new char[30];
        Arrays.fill(bits, '0');
        for (Integer n : numbers) {
            bits[n - 1] = '1';
        }
        return new String(bits);
    }

    private CourseSchedule course(long roomId, int sh, int sm, int eh, int em, String weeks) {
        CourseSchedule c = new CourseSchedule();
        c.setRoomId(roomId);
        c.setCourseName("测试课程");
        c.setDayOfWeek(MON.getDayOfWeek().getValue());
        c.setStartTime(LocalTime.of(sh, sm));
        c.setEndTime(LocalTime.of(eh, em));
        c.setWeeks(weeks);
        return c;
    }

    private Reservation reservation(long resourceId, int sh, int sm, int eh, int em, int headcount) {
        Reservation r = new Reservation();
        r.setResourceId(resourceId);
        r.setUserId(9L);
        r.setStatus(Reservation.STATUS_PENDING);
        r.setHeadcount(headcount);
        r.setStartTime(MON.atTime(sh, sm));
        r.setEndTime(MON.atTime(eh, em));
        return r;
    }

    // ---------------- freeResources ----------------

    @Test
    @DisplayName("freeResources：被课程占用的实验室不出现，体育馆按容量扣减剩余")
    void freeResources_excludesCourseAndCountsGymCapacity() {
        when(resourceMapper.selectList(any()))
                .thenReturn(List.of(resource(1L, 2, 30), resource(2L, 4, 20)));
        when(courseScheduleMapper.selectList(any()))
                .thenReturn(List.of(course(1L, 10, 0, 12, 0, weeks(1, 2, 3, 4, 5, 6, 7, 8))));
        when(reservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(2L, 10, 0, 12, 0, 8)));

        List<Map<String, Object>> list = service.freeResources(MON, LocalTime.of(10, 0),
                LocalTime.of(11, 0), 2, null, null, null, null, null);

        assertThat(list).hasSize(1);
        Map<String, Object> gym = list.get(0);
        assertThat(gym.get("id")).isEqualTo(2L);
        assertThat(gym.get("remaining")).isEqualTo(12);
    }

    @Test
    @DisplayName("freeResources：同一门课只在部分周次上课，非上课周次不算占用")
    void freeResources_ignoresCourseInOtherWeeks() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource(1L, 2, 30)));
        when(courseScheduleMapper.selectList(any()))
                .thenReturn(List.of(course(1L, 10, 0, 12, 0, weeks(7))));

        // 第 7 周：被占用
        assertThat(service.freeResources(MON, LocalTime.of(10, 0), LocalTime.of(11, 0),
                2, null, null, null, null, null)).isEmpty();
        // 第 8 周（2026-10-26）：不上课，应可约
        assertThat(service.freeResources(MON.plusWeeks(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                2, null, null, null, null, null)).hasSize(1);
    }

    @Test
    @DisplayName("freeResources：所选时段超出开放时间 → 不返回该资源")
    void freeResources_rejectsOutsideOpenHours() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource(1L, 2, 30)));

        // 实验室 08:00-22:00，查 21:00-23:00 越界
        assertThat(service.freeResources(MON, LocalTime.of(21, 0), LocalTime.of(23, 0),
                2, null, null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("freeResources：一键闭馆 → 400 并给出原因，而不是空列表")
    void freeResources_closedCampus_throws() {
        when(systemConfigService.isClosed()).thenReturn(true);

        assertThatThrownBy(() -> service.freeResources(MON, LocalTime.of(10, 0), LocalTime.of(11, 0),
                2, null, null, null, null, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("闭馆");
    }

    @Test
    @DisplayName("freeResources：实验室/体育馆超过 7 天 → 400")
    void freeResources_beyondAdvanceDays_throws() {
        // 提前天数以“今天”为基准，不能写死日期，否则测试会随时间失效
        assertThatThrownBy(() -> service.freeResources(LocalDate.now().plusDays(8), LocalTime.of(10, 0),
                LocalTime.of(11, 0), 2, null, null, null, null, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("一周内");
    }

    // ---------------- daySchedule ----------------

    @Test
    @DisplayName("daySchedule：课程与预约一起列入 busySlots，并切出空闲窗口")
    @SuppressWarnings("unchecked")
    void daySchedule_listsBusyKindsAndFreeWindows() {
        Resource lab = resource(1L, 2, 30);
        when(courseScheduleMapper.selectList(any()))
                .thenReturn(List.of(course(1L, 10, 0, 12, 0, weeks(7))));
        when(reservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(1L, 14, 0, 16, 0, 3)));

        Map<String, Object> data = service.daySchedule(lab, MON);

        assertThat(data.get("closed")).isEqualTo(false);
        assertThat(data.get("week")).isEqualTo(7);
        List<Map<String, Object>> busy = (List<Map<String, Object>>) data.get("busySlots");
        assertThat(busy).hasSize(2);
        assertThat(busy.get(0).get("kind")).isEqualTo("course");
        assertThat(busy.get(1).get("kind")).isEqualTo("reservation");
        assertThat((List<String>) data.get("freeSlots"))
                .containsExactly("08:00-10:00", "12:00-14:00", "16:00-22:00");
    }

    @Test
    @DisplayName("daySchedule：闭馆当天返回 closed=true 与原因")
    void daySchedule_closedDay() {
        when(systemConfigService.isClosed()).thenReturn(true);

        Map<String, Object> data = service.daySchedule(resource(1L, 2, 30), MON);

        assertThat(data.get("closed")).isEqualTo(true);
        assertThat(String.valueOf(data.get("closedReason"))).contains("闭馆");
    }

    // ---------------- check ----------------

    @Test
    @DisplayName("check：时段内有课 → 不可用且原因写明课程")
    void check_blockedByCourse() {
        Resource lab = resource(1L, 2, 30);
        when(courseScheduleMapper.selectList(any()))
                .thenReturn(List.of(course(1L, 10, 0, 12, 0, weeks(7))));

        AvailabilityService.Availability a = service.check(lab, MON.atTime(10, 30), MON.atTime(11, 30));

        assertThat(a.available()).isFalse();
        assertThat(a.reason()).contains("测试课程");
    }

    @Test
    @DisplayName("check：体育馆剩余容量不足由调用方结合 remaining 判断")
    void check_gymRemaining() {
        Resource gym = resource(2L, 4, 20);
        when(reservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(2L, 10, 0, 12, 0, 15)));

        AvailabilityService.Availability a = service.check(gym, MON.atTime(10, 0), MON.atTime(11, 0));

        assertThat(a.available()).isTrue();
        assertThat(a.remaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("check：时段顺序颠倒 → 不可用")
    void check_invalidRange() {
        AvailabilityService.Availability a = service.check(resource(1L, 2, 30),
                MON.atTime(11, 0), MON.atTime(10, 0));

        assertThat(a.available()).isFalse();
        assertThat(a.reason()).contains("不合法");
    }
}

package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.dto.CourseImportDTO;
import com.campus.reservation.entity.CourseSchedule;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CourseScheduleMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CourseService 单元测试：课表导入校验 + available 可预约时段合并算法
 * （开放时间 - 课程占用 - 已有预约，含跨边界裁剪、乱序区间、全天占满等边界）。
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseScheduleMapper courseScheduleMapper;
    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private ReservationMapper reservationMapper;

    @InjectMocks
    private CourseService service;

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19); // 周一

    private Resource room() {
        Resource r = new Resource();
        r.setId(1L);
        r.setName("A101 教室");
        r.setOpenTime(LocalTime.of(8, 0));
        r.setCloseTime(LocalTime.of(22, 0));
        return r;
    }

    private CourseSchedule course(int startH, int startM, int endH, int endM) {
        CourseSchedule c = new CourseSchedule();
        c.setRoomId(1L);
        c.setCourseName("测试课程");
        c.setDayOfWeek(1);
        c.setStartTime(LocalTime.of(startH, startM));
        c.setEndTime(LocalTime.of(endH, endM));
        return c;
    }

    private Reservation reservation(int startH, int startM, int endH, int endM) {
        Reservation r = new Reservation();
        r.setResourceId(1L);
        r.setStartTime(MONDAY.atTime(startH, startM));
        r.setEndTime(MONDAY.atTime(endH, endM));
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> freeSlots(Map<String, Object> data) {
        return (List<Map<String, Object>>) data.get("freeSlots");
    }

    // ---------------- importCourses ----------------

    private CourseImportDTO validImportDto() {
        CourseImportDTO dto = new CourseImportDTO();
        dto.setRoomId(1L);
        dto.setCourseName("高等数学");
        dto.setDayOfWeek(1);
        dto.setStartTime(LocalTime.of(8, 0));
        dto.setEndTime(LocalTime.of(9, 40));
        return dto;
    }

    @Test
    @DisplayName("importCourses：空列表 → 400")
    void importCourses_empty_badRequest() {
        assertThatThrownBy(() -> service.importCourses(List.of()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        assertThatThrownBy(() -> service.importCourses(null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("importCourses：星期非法（0 或 8）→ 400")
    void importCourses_badDayOfWeek_badRequest() {
        CourseImportDTO dto = validImportDto();
        dto.setDayOfWeek(8);
        assertThatThrownBy(() -> service.importCourses(List.of(dto)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("importCourses：结束时间不晚于开始时间 → 400")
    void importCourses_reversedTime_badRequest() {
        CourseImportDTO dto = validImportDto();
        dto.setEndTime(dto.getStartTime());
        assertThatThrownBy(() -> service.importCourses(List.of(dto)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(courseScheduleMapper, never()).insert(any(CourseSchedule.class));
    }

    @Test
    @DisplayName("importCourses：全部合法 → 返回导入条数")
    void importCourses_success() {
        int count = service.importCourses(List.of(validImportDto(), validImportDto()));
        assertThat(count).isEqualTo(2);
        verify(courseScheduleMapper, org.mockito.Mockito.times(2)).insert(any(CourseSchedule.class));
    }

    // ---------------- available：时段合并算法 ----------------

    @Test
    @DisplayName("available：教室不存在 → 404")
    void available_roomNotFound() {
        when(resourceMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.available(1L, MONDAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    @DisplayName("available：无课无约 → 整段开放时间可约")
    void available_fullyFree() {
        when(resourceMapper.selectById(1L)).thenReturn(room());
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.available(1L, MONDAY);

        assertThat(freeSlots(data)).hasSize(1);
        assertThat(freeSlots(data).get(0).get("start")).isEqualTo(LocalTime.of(8, 0));
        assertThat(freeSlots(data).get(0).get("end")).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("available：两节课占用 → 切出三段空闲（8-10 / 12-14 / 16-22）")
    void available_splitByCourses() {
        when(resourceMapper.selectById(1L)).thenReturn(room());
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of(
                course(10, 0, 12, 0), course(14, 0, 16, 0)));
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.available(1L, MONDAY);
        List<Map<String, Object>> slots = freeSlots(data);

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).get("start")).isEqualTo(LocalTime.of(8, 0));
        assertThat(slots.get(0).get("end")).isEqualTo(LocalTime.of(10, 0));
        assertThat(slots.get(1).get("start")).isEqualTo(LocalTime.of(12, 0));
        assertThat(slots.get(1).get("end")).isEqualTo(LocalTime.of(14, 0));
        assertThat(slots.get(2).get("start")).isEqualTo(LocalTime.of(16, 0));
        assertThat(slots.get(2).get("end")).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("available：预约与课程乱序、重叠 → 合并去重不产生负长度时段")
    void available_overlappingUnsortedIntervals() {
        when(resourceMapper.selectById(1L)).thenReturn(room());
        // 乱序给出且互相重叠：10-12 与 11-13
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of(
                course(11, 0, 13, 0), course(10, 0, 12, 0)));
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> slots = freeSlots(service.available(1L, MONDAY));

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).get("end")).isEqualTo(LocalTime.of(10, 0));
        assertThat(slots.get(1).get("start")).isEqualTo(LocalTime.of(13, 0));
        assertThat(slots.get(1).get("end")).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("available：预约跨越开放边界 → 裁剪到开放时段内（7-9 点占用视为 8-9）")
    void available_reservationClippedToOpenHours() {
        when(resourceMapper.selectById(1L)).thenReturn(room());
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation(7, 0, 9, 0)));

        List<Map<String, Object>> slots = freeSlots(service.available(1L, MONDAY));

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).get("start")).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(0).get("end")).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("available：全天被占满 → 无空闲时段")
    void available_fullyBooked() {
        when(resourceMapper.selectById(1L)).thenReturn(room());
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of(course(8, 0, 22, 0)));
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        assertThat(freeSlots(service.available(1L, MONDAY))).isEmpty();
    }

    // ---------------- freeRooms：按时段批量筛选可选教室 ----------------

    private Resource roomOf(long id, String building, String floor, String roomNo) {
        Resource r = new Resource();
        r.setId(id);
        r.setName(roomNo + " 教室");
        r.setResourceType(1);
        r.setStatus(1);
        r.setBuilding(building);
        r.setFloor(floor);
        r.setRoomNo(roomNo);
        r.setCapacity(40);
        r.setOpenTime(LocalTime.of(8, 0));
        r.setCloseTime(LocalTime.of(22, 0));
        return r;
    }

    private CourseSchedule courseOf(long roomId, int sh, int sm, int eh, int em) {
        CourseSchedule c = course(sh, sm, eh, em);
        c.setRoomId(roomId);
        return c;
    }

    private Reservation reservationOf(long roomId, int sh, int sm, int eh, int em) {
        Reservation r = reservation(sh, sm, eh, em);
        r.setResourceId(roomId);
        return r;
    }

    @Test
    @DisplayName("freeRooms：结束时间不晚于开始时间 → 400，不查库")
    void freeRooms_badTimeRange_badRequest() {
        assertThatThrownBy(() -> service.freeRooms(MONDAY, LocalTime.of(16, 0), LocalTime.of(14, 0), List.of(), null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(resourceMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("freeRooms：无候选教室 → 返回空且不查课表")
    void freeRooms_noCandidateRooms_returnsEmpty() {
        when(resourceMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.freeRooms(MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0), List.of("教学楼A栋"), null))
                .isEmpty();
        verify(courseScheduleMapper, never()).selectList(any());
        verify(reservationMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("freeRooms：无课无约 → 全部可选，可坐到闭馆")
    void freeRooms_allFree() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(
                roomOf(1L, "教学楼A栋", "1F", "A101"), roomOf(2L, "教学楼A栋", "1F", "A102")));
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> free =
                service.freeRooms(MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0), List.of(), null);

        assertThat(free).hasSize(2);
        assertThat(free.get(0)).containsEntry("roomNo", "A101")
                .containsEntry("freeUntil", LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("freeRooms：时段与课程重叠的教室被排除，其余保留")
    void freeRooms_excludesRoomWithOverlappingCourse() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(
                roomOf(1L, "教学楼A栋", "1F", "A101"), roomOf(2L, "教学楼A栋", "1F", "A102")));
        // A101 在 15:00-17:00 有课，与查询的 14:00-16:00 重叠
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of(courseOf(1L, 15, 0, 17, 0)));
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> free =
                service.freeRooms(MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0), List.of(), null);

        assertThat(free).hasSize(1);
        assertThat(free.get(0).get("roomNo")).isEqualTo("A102");
    }

    @Test
    @DisplayName("freeRooms：课程紧接时段之后 → 可选且 freeUntil 为课程开始")
    void freeRooms_freeUntilNextCourse() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(roomOf(1L, "教学楼A栋", "1F", "A101")));
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of(courseOf(1L, 16, 0, 17, 40)));
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> free =
                service.freeRooms(MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0), List.of(), null);

        assertThat(free).hasSize(1);
        assertThat(free.get(0).get("freeUntil")).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    @DisplayName("freeRooms：时段未被开放时间完整覆盖 → 排除，避免中途离场")
    void freeRooms_excludesRoomNotCoveringWholeSpan() {
        Resource lateClose = roomOf(1L, "教学楼A栋", "1F", "A101");
        lateClose.setCloseTime(LocalTime.of(21, 0));
        when(resourceMapper.selectList(any())).thenReturn(List.of(lateClose));
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        // 20:00-22:00 超出 21:00 关馆
        assertThat(service.freeRooms(MONDAY, LocalTime.of(20, 0), LocalTime.of(22, 0), List.of(), null))
                .isEmpty();
    }

    @Test
    @DisplayName("freeRooms：他人预约同样视为占用")
    void freeRooms_excludesRoomWithReservation() {
        when(resourceMapper.selectList(any())).thenReturn(List.of(roomOf(1L, "教学楼A栋", "1F", "A101")));
        when(courseScheduleMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservationOf(1L, 14, 30, 15, 0)));

        assertThat(service.freeRooms(MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0), List.of(), null))
                .isEmpty();
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("delete：记录不存在 → 404 且不执行删除")
    void delete_notFound() {
        when(courseScheduleMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
        verify(courseScheduleMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("delete：记录存在 → 执行 deleteById")
    void delete_success() {
        when(courseScheduleMapper.selectById(1L)).thenReturn(course(8, 0, 9, 0));
        service.delete(1L);
        verify(courseScheduleMapper).deleteById(1L);
    }
}

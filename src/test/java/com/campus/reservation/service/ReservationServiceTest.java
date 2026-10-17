package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ReservationDTO;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.mapper.HolidayMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.ViolationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReservationService 单元测试：Mock 全部 Mapper 与外部 Service，不启动 Spring、不连接 MySQL。
 * 重点覆盖 create 的核心业务规则（参数/信用分/资源状态/时段冲突/容量等校验分支）与状态流转（取消/打卡/提前释放/暂离/爽约自动流转）。
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private HolidayMapper holidayMapper;
    @Mock
    private ViolationMapper violationMapper;
    @Mock
    private ReservationLogService logService;
    @Mock
    private CheckinCodeService checkinCodeService;
    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private ReservationService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------------- 构造辅助 ----------------

    private Resource openResource(int type, int status) {
        Resource r = new Resource();
        r.setId(1L);
        r.setName("测试资源");
        r.setResourceType(type);
        r.setStatus(status);
        r.setOpenTime(LocalTime.of(8, 0));
        r.setCloseTime(LocalTime.of(22, 0));
        r.setMaxDuration(120);
        r.setCapacity(10);
        return r;
    }

    private SysUser validUser() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setStatus(1);
        u.setCreditScore(100);
        return u;
    }

    private ReservationDTO validDto() {
        ReservationDTO dto = new ReservationDTO();
        dto.setResourceId(1L);
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        dto.setStartTime(start);
        dto.setEndTime(start.plusHours(1));
        dto.setHeadcount(1);
        return dto;
    }

    // ---------------- create：核心业务规则 ----------------

    @Test
    @DisplayName("create：缺少必要参数 → 400")
    void create_missingParams_badRequest() {
        UserContext.set(1L, 0);
        ReservationDTO dto = new ReservationDTO();
        dto.setResourceId(null);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("create：当日违规达 3 次 → 403")
    void create_todayViolationsAtLimit_forbidden() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(violationMapper.selectCount(any())).thenReturn(3L);
        assertThatThrownBy(() -> service.create(validDto()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 403);
    }

    @Test
    @DisplayName("create：资源不存在 → 404")
    void create_resourceNotFound_notFound() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(validDto()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    @DisplayName("create：资源维修中 → 409")
    void create_resourceMaintaining_conflict() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 3));
        assertThatThrownBy(() -> service.create(validDto()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 409);
    }

    @Test
    @DisplayName("create：教室无需预约 → 400")
    void create_classroom_badRequest() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(1, 1));
        assertThatThrownBy(() -> service.create(validDto()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("create：结束时间早于开始时间 → 400")
    void create_reversedTime_badRequest() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        ReservationDTO dto = validDto();
        dto.setEndTime(dto.getStartTime().minusHours(1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("create：预约时长不设上限 → 长时段也能创建成功")
    void create_longDuration_allowed() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(holidayMapper.selectCount(any())).thenReturn(0L);
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        ReservationDTO dto = validDto();
        dto.setEndTime(dto.getStartTime().plusHours(6)); // 360 分钟，旧规则会拒绝

        Reservation r = service.create(dto);
        assertThat(r.getStatus()).isEqualTo(Reservation.STATUS_PENDING);
    }

    @Test
    @DisplayName("create：节假日暂停预约 → 409")
    void create_holiday_conflict() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(holidayMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.create(validDto()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 409);
    }

    @Test
    @DisplayName("create：时段与已有预约冲突（非体育馆）→ 409")
    void create_timeConflict_conflict() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(holidayMapper.selectCount(any())).thenReturn(0L);

        ReservationDTO dto = validDto();
        Reservation exist = new Reservation();
        exist.setStartTime(dto.getStartTime());
        exist.setEndTime(dto.getEndTime());
        // 第一次查询是"本人同时间段冲突"校验（无），第二次是"资源时段冲突"校验（有）
        when(reservationMapper.selectList(any())).thenReturn(List.of(), List.of(exist));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 409);
    }

    @Test
    @DisplayName("create：体育馆容量不足 → 409")
    void create_gymCapacityOverflow_conflict() {
        UserContext.set(1L, 0);
        Resource gym = openResource(4, 1);
        gym.setCapacity(2);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(gym);
        when(holidayMapper.selectCount(any())).thenReturn(0L);

        ReservationDTO dto = validDto();
        dto.setHeadcount(1);
        Reservation exist = new Reservation();
        exist.setHeadcount(2);
        exist.setStartTime(dto.getStartTime());
        exist.setEndTime(dto.getEndTime());
        // 第一次查询是"本人同时间段冲突"校验（无），第二次返回该时段已占用的体育馆预约
        when(reservationMapper.selectList(any())).thenReturn(List.of(), List.of(exist));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 409);
    }

    @Test
    @DisplayName("create：成功创建预约 → 待打卡状态")
    void create_success_pending() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(holidayMapper.selectCount(any())).thenReturn(0L);
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        Reservation r = service.create(validDto());
        assertThat(r.getStatus()).isEqualTo(Reservation.STATUS_PENDING);
        assertThat(r.getHeadcount()).isEqualTo(1);
        assertThat(r.getCheckinFlag()).isEqualTo(0);
    }

    @Test
    @DisplayName("create：图书馆预约超过次日 → 400")
    void create_libraryBeyondTomorrow_badRequest() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        ReservationDTO dto = validDto();
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        dto.setStartTime(start);
        dto.setEndTime(start.plusHours(1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("create：体育馆预约超过未来一周 → 400")
    void create_gymBeyondWeek_badRequest() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(4, 1));
        ReservationDTO dto = validDto();
        LocalDateTime start = LocalDateTime.now().plusDays(8).withHour(10).withMinute(0).withSecond(0).withNano(0);
        dto.setStartTime(start);
        dto.setEndTime(start.plusHours(1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("create：闭馆期间普通用户 → 403")
    void create_campusClosed_forbidden() {
        UserContext.set(1L, 0);
        when(userMapper.selectById(1L)).thenReturn(validUser());
        when(systemConfigService.isClosed()).thenReturn(true);
        assertThatThrownBy(() -> service.create(validDto()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 403);
    }

    @Test
    @DisplayName("create：管理员可预约图书馆超过次日的日期 → 成功")
    void create_adminBypassAdvanceLimit_success() {
        UserContext.set(99L, 1);
        SysUser admin = validUser();
        admin.setId(99L);
        when(userMapper.selectById(99L)).thenReturn(admin);
        Resource lib = openResource(3, 1);
        when(resourceMapper.selectById(1L)).thenReturn(lib);
        when(holidayMapper.selectCount(any())).thenReturn(0L);
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        ReservationDTO dto = validDto();
        LocalDateTime start = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);
        dto.setStartTime(start);
        dto.setEndTime(start.plusHours(1));

        Reservation r = service.create(dto);
        assertThat(r.getStatus()).isEqualTo(Reservation.STATUS_PENDING);
        // 管理员不走当日违规与闭馆拦截
        verify(violationMapper, never()).selectCount(any());
    }

    // ---------------- 状态流转 ----------------

    @Test
    @DisplayName("cancel：取消待打卡预约 → 已取消")
    void cancel_success_cancelled() {
        UserContext.set(1L, 0);
        Reservation r = new Reservation();
        r.setId(1L);
        r.setUserId(1L);
        r.setResourceId(1L);
        r.setStatus(Reservation.STATUS_PENDING);
        r.setStartTime(LocalDateTime.now().plusHours(1));
        r.setEndTime(LocalDateTime.now().plusHours(2));
        when(reservationMapper.selectById(1L)).thenReturn(r);
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(reservationMapper.selectCount(any())).thenReturn(0L);

        Reservation result = service.cancel(1L);
        assertThat(result.getStatus()).isEqualTo(Reservation.STATUS_CANCELLED);
    }

    @Test
    @DisplayName("checkin：待打卡 → 使用中并标记已打卡")
    void checkin_success_inUse() {
        UserContext.set(1L, 0);
        Reservation r = new Reservation();
        r.setId(1L);
        r.setUserId(1L);
        r.setResourceId(1L);
        r.setStatus(Reservation.STATUS_PENDING);
        r.setStartTime(LocalDateTime.now().minusMinutes(1));
        r.setEndTime(LocalDateTime.now().plusHours(1));
        when(reservationMapper.selectById(1L)).thenReturn(r);
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(resourceMapper.selectBatchIds(any())).thenReturn(List.of(openResource(3, 1)));

        Reservation result = service.checkin(1L);
        assertThat(result.getStatus()).isEqualTo(Reservation.STATUS_IN_USE);
        assertThat(result.getCheckinFlag()).isEqualTo(1);
    }

    @Test
    @DisplayName("release：提前释放使用中的预约 → 已完成")
    void release_success_completed() {
        UserContext.set(1L, 0);
        Reservation r = new Reservation();
        r.setId(1L);
        r.setUserId(1L);
        r.setResourceId(1L);
        r.setStatus(Reservation.STATUS_IN_USE);
        when(reservationMapper.selectById(1L)).thenReturn(r);
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(resourceMapper.selectBatchIds(any())).thenReturn(List.of(openResource(3, 1)));

        Reservation result = service.release(1L);
        assertThat(result.getStatus()).isEqualTo(Reservation.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("away：暂离 30 分钟设置暂离截止时间")
    void away_success_setsAwayUntil() {
        UserContext.set(1L, 0);
        Reservation r = new Reservation();
        r.setId(1L);
        r.setUserId(1L);
        r.setResourceId(1L);
        r.setStatus(Reservation.STATUS_IN_USE);
        r.setStartTime(LocalDateTime.now().minusHours(1));
        r.setEndTime(LocalDateTime.now().plusHours(1));
        when(reservationMapper.selectById(1L)).thenReturn(r);
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));
        when(resourceMapper.selectBatchIds(any())).thenReturn(List.of(openResource(3, 1)));

        Reservation result = service.away(1L, 30);
        assertThat(result.getAwayUntil()).isNotNull();
    }

    @Test
    @DisplayName("delete：删除已结束的预约并调用 deleteById")
    void delete_success_deletesFinished() {
        UserContext.set(1L, 0);
        Reservation r = new Reservation();
        r.setId(1L);
        r.setUserId(1L);
        r.setResourceId(1L);
        r.setStatus(Reservation.STATUS_COMPLETED);
        r.setStartTime(LocalDateTime.now().minusHours(2));
        r.setEndTime(LocalDateTime.now().minusHours(1));
        when(reservationMapper.selectList(any())).thenReturn(List.of()); // refreshStatuses 三次查询均无记录
        when(reservationMapper.selectById(1L)).thenReturn(r);
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));

        service.delete(1L);
        verify(reservationMapper).deleteById(1L);
    }

    @Test
    @DisplayName("refreshStatuses：超时未打卡 → 仅记一次爽约违规，不再扣分")
    void refreshStatuses_noshow_recordsViolation() {
        Reservation r = new Reservation();
        r.setId(1L);
        r.setUserId(1L);
        r.setResourceId(1L);
        r.setStatus(Reservation.STATUS_PENDING);
        r.setStartTime(LocalDateTime.now().minusHours(1));
        when(reservationMapper.selectList(any())).thenReturn(List.of(r), List.of(), List.of());
        when(resourceMapper.selectById(1L)).thenReturn(openResource(3, 1));

        service.refreshStatuses();

        verify(violationMapper).insert(any(Violation.class));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }
}

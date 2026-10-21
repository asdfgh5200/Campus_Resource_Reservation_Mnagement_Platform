package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ClosureDTO;
import com.campus.reservation.entity.CampusClosure;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CampusClosureMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ClosureService 单元测试：闭馆时段重叠判定、占用拦截、自动取消并通知。
 */
@ExtendWith(MockitoExtension.class)
class ClosureServiceTest {

    @Mock
    private CampusClosureMapper closureMapper;
    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationLogService logService;
    @Mock
    private MessageService messageService;

    @InjectMocks
    private ClosureService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private CampusClosure closure(int type, LocalDate date, String start, String end) {
        CampusClosure c = new CampusClosure();
        c.setId(1L);
        c.setResourceType(type);
        c.setClosureDate(date);
        c.setStartTime(LocalTime.parse(start));
        c.setEndTime(LocalTime.parse(end));
        c.setEnabled(CampusClosure.ENABLED);
        return c;
    }

    @Test
    @DisplayName("isClosedAt：类型与时段重叠 → true；类型不符/时段不相交 → false")
    void isClosedAt_overlapDetection() {
        LocalDate d = LocalDate.now().plusDays(2);
        when(closureMapper.selectList(any())).thenReturn(List.of(closure(3, d, "10:00", "12:00")));

        // 10:30~11:00 图书馆，落在闭馆窗口内
        assertThat(service.isClosedAt(3, LocalDateTime.of(d, LocalTime.of(10, 30)),
                LocalDateTime.of(d, LocalTime.of(11, 0)))).isTrue();
        // 同类型但 13:00 之后，不相交
        assertThat(service.isClosedAt(3, LocalDateTime.of(d, LocalTime.of(13, 0)),
                LocalDateTime.of(d, LocalTime.of(14, 0)))).isFalse();
        // 类型不符
        assertThat(service.isClosedAt(2, LocalDateTime.of(d, LocalTime.of(10, 30)),
                LocalDateTime.of(d, LocalTime.of(11, 0)))).isFalse();
    }

    @Test
    @DisplayName("isClosedAt：仅边界相接不算重叠（半开区间）")
    void isClosedAt_boundaryNotOverlap() {
        LocalDate d = LocalDate.now().plusDays(2);
        when(closureMapper.selectList(any())).thenReturn(List.of(closure(3, d, "10:00", "12:00")));
        // 预约 12:00 起，闭馆 12:00 止：start.isBefore(cEnd=12:00) 为 false → 不相交
        assertThat(service.isClosedAt(3, LocalDateTime.of(d, LocalTime.of(12, 0)),
                LocalDateTime.of(d, LocalTime.of(13, 0)))).isFalse();
    }

    @Test
    @DisplayName("create：非管理员 → 403")
    void create_nonAdmin_forbidden() {
        UserContext.set(10L, 0);
        assertThatThrownBy(() -> service.create(new ClosureDTO()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 403);
    }

    @Test
    @DisplayName("create：闭馆窗口覆盖当前且该类型有资源使用中 → 409，不落库")
    void create_inUseGuard_conflict() {
        UserContext.set(1L, 1);
        ClosureDTO dto = new ClosureDTO();
        dto.setResourceType(3);
        dto.setClosureDate(LocalDate.now());
        dto.setStartTime(LocalTime.of(0, 0));
        dto.setEndTime(LocalTime.of(23, 59));

        Resource r = new Resource();
        r.setId(7L);
        when(resourceMapper.selectList(any())).thenReturn(List.of(r));
        when(reservationMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 409);
        verify(closureMapper, never()).insert(any(CampusClosure.class));
    }

    @Test
    @DisplayName("create：未来闭馆自动取消重叠预约并逐条通知")
    void create_futureClosure_cancelsAndNotifies() {
        UserContext.set(1L, 1);
        ClosureDTO dto = new ClosureDTO();
        dto.setResourceType(2);
        dto.setClosureDate(LocalDate.now().plusDays(3));
        dto.setStartTime(LocalTime.of(14, 0));
        dto.setEndTime(LocalTime.of(16, 0));
        dto.setReason("设备检修");

        Resource res = new Resource();
        res.setId(5L);
        res.setName("3号楼实验室");
        when(resourceMapper.selectList(any())).thenReturn(List.of(res));
        when(resourceMapper.selectById(anyLong())).thenReturn(res);

        Reservation affected = new Reservation();
        affected.setId(100L);
        affected.setUserId(20L);
        affected.setResourceId(5L);
        affected.setStartTime(LocalDateTime.now().plusDays(3).withHour(14).withMinute(30));
        affected.setEndTime(LocalDateTime.now().plusDays(3).withHour(15).withMinute(30));
        affected.setStatus(Reservation.STATUS_PENDING);
        when(reservationMapper.selectList(any())).thenReturn(List.of(affected));

        int cancelled = service.create(dto);

        assertThat(cancelled).isEqualTo(1);
        verify(closureMapper).insert(any(CampusClosure.class));
        verify(reservationMapper).updateById(affected);
        assertThat(affected.getStatus()).isEqualTo(Reservation.STATUS_CANCELLED);
        verify(logService).record(eq(20L), eq(100L), eq(5L), anyString(), anyInt(), anyString());
        verify(messageService, times(1)).notifyUser(eq(20L), anyString());
    }

    @Test
    @DisplayName("revoke：置为不生效")
    void revoke_disablesClosure() {
        UserContext.set(1L, 1);
        CampusClosure c = closure(3, LocalDate.now().plusDays(1), "09:00", "10:00");
        when(closureMapper.selectById(1L)).thenReturn(c);

        service.revoke(1L);

        assertThat(c.getEnabled()).isEqualTo(CampusClosure.DISABLED);
        verify(closureMapper).updateById(c);
    }
}

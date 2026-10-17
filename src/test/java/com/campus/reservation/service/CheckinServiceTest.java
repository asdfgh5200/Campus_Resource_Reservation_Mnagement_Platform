package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.entity.CheckinCode;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CheckinService 单元测试：验证码打卡校验分支。
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock
    private CheckinCodeService checkinCodeService;
    @Mock
    private ResourceService resourceService;
    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private CheckinService service;

    private Reservation reservationOf(long resourceId) {
        Reservation r = new Reservation();
        r.setId(1L);
        r.setResourceId(resourceId);
        return r;
    }

    // ---------------- getCode ----------------

    @Test
    @DisplayName("getCode：资源不存在 → 404（由 ResourceService 抛出）")
    void getCode_resourceNotFound() {
        when(resourceService.getById(99L)).thenThrow(BizException.notFound("资源不存在"));
        assertThatThrownBy(() -> service.getCode(99L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    @DisplayName("getCode：正常返回当日验证码信息")
    void getCode_success() {
        when(resourceService.getById(1L)).thenReturn(new Resource());
        CheckinCode code = new CheckinCode();
        code.setCode("246810");
        when(checkinCodeService.ensureTodayCode(1L)).thenReturn(code);

        Map<String, Object> data = service.getCode(1L);

        assertThat(data.get("resourceId")).isEqualTo(1L);
        assertThat(data.get("code")).isEqualTo("246810");
    }

    // ---------------- verify ----------------

    @Test
    @DisplayName("verify：预约 ID 为空 → 400")
    void verify_nullReservationId_badRequest() {
        assertThatThrownBy(() -> service.verify(null, "123456"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("verify：验证码为空/空白 → 400，不做任何查询")
    void verify_blankCode_badRequest() {
        assertThatThrownBy(() -> service.verify(1L, "  "))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(checkinCodeService, never()).findTodayCode(any());
    }

    @Test
    @DisplayName("verify：该资源当日无验证码 → 400")
    void verify_noCodeToday_badRequest() {
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(checkinCodeService.findTodayCode(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.verify(1L, "123456"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(reservationService, never()).checkin(anyLong());
    }

    @Test
    @DisplayName("verify：验证码不匹配 → 400")
    void verify_wrongCode_badRequest() {
        CheckinCode today = new CheckinCode();
        today.setCode("111111");
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(checkinCodeService.findTodayCode(5L)).thenReturn(today);

        assertThatThrownBy(() -> service.verify(1L, "222222"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(reservationService, never()).checkin(anyLong());
    }

    @Test
    @DisplayName("verify：验证码正确（含首尾空格）→ 委托完成打卡")
    void verify_correctCode_delegatesCheckin() {
        CheckinCode today = new CheckinCode();
        today.setCode("111111");
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(checkinCodeService.findTodayCode(5L)).thenReturn(today);
        when(reservationService.checkin(1L)).thenReturn(reservationOf(5L));

        service.verify(1L, "  111111  ");

        verify(reservationService).checkin(1L);
    }
}

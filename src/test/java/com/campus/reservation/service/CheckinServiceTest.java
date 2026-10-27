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

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CheckinService 单元测试：验证码打卡、定位打卡校验分支。
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

    private Resource resourceOf(long id, int type, double latitude, double longitude) {
        Resource r = new Resource();
        r.setId(id);
        r.setName("测试资源");
        r.setBuilding("教学楼A栋");
        r.setResourceType(type);
        r.setLatitude(BigDecimal.valueOf(latitude));
        r.setLongitude(BigDecimal.valueOf(longitude));
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

    // ---------------- verifyLocation ----------------

    @Test
    @DisplayName("verifyLocation：缺少定位参数 → 400")
    void verifyLocation_missingCoordinate_badRequest() {
        assertThatThrownBy(() -> service.verifyLocation(1L, null, 114.333556, 20.0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(reservationService, never()).detail(anyLong());
    }

    @Test
    @DisplayName("verifyLocation：坐标超出合法范围 → 400")
    void verifyLocation_invalidCoordinate_badRequest() {
        assertThatThrownBy(() -> service.verifyLocation(1L, 91.0, 114.333556, 20.0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("verifyLocation：定位精度劣于 100 米 → 400")
    void verifyLocation_poorAccuracy_badRequest() {
        assertThatThrownBy(() -> service.verifyLocation(1L, 30.511442, 114.333556, 200.0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("精度");
    }

    @Test
    @DisplayName("verifyLocation：资源未配置打卡坐标 → 400")
    void verifyLocation_resourceWithoutCoordinate_badRequest() {
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(resourceService.getById(5L)).thenReturn(new Resource());

        assertThatThrownBy(() -> service.verifyLocation(1L, 30.511442, 114.333556, 20.0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("暂未设置");
        verify(reservationService, never()).checkin(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("verifyLocation：超出 120 米半径且超出精度容差 → 400 并提示实际距离")
    void verifyLocation_outOfRange_badRequest() {
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(resourceService.getById(5L)).thenReturn(resourceOf(5L, 1, 30.511442, 114.333556));

        // 纬度 +0.003 度 → 约 334 米，超过 120 + 容差 20 = 140 米
        assertThatThrownBy(() -> service.verifyLocation(1L, 30.514442, 114.333556, 20.0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("米")
                .hasMessageContaining("靠近");
        verify(reservationService, never()).checkin(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("verifyLocation：略超基础半径但落在精度容差内 → 打卡成功")
    void verifyLocation_withinAccuracyTolerance_success() {
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(resourceService.getById(5L)).thenReturn(resourceOf(5L, 1, 30.511442, 114.333556));
        when(reservationService.checkin(eq(1L), eq(Reservation.CHECKIN_TYPE_LOCATION), anyString(),
                any(), any(), any(), any())).thenReturn(reservationOf(5L));

        // 纬度 +0.0014 度 → 约 156 米，超出 120 米但落在 120 + 50 = 170 米内
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) service.verifyLocation(1L, 30.512842, 114.333556, 50.0);

        assertThat((Long) data.get("distance")).isBetween(150L, 160L);
        assertThat(data.get("radius")).isEqualTo(120L);
        assertThat(data.get("allowedRadius")).isEqualTo(170L);
    }

    @Test
    @DisplayName("verifyLocation：同样距离但精度容差不足 → 400")
    void verifyLocation_toleranceInsufficient_badRequest() {
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(resourceService.getById(5L)).thenReturn(resourceOf(5L, 1, 30.511442, 114.333556));

        // 约 156 米，精度 10 米 → 允许 130 米，仍拒绝
        assertThatThrownBy(() -> service.verifyLocation(1L, 30.512842, 114.333556, 10.0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400)
                .hasMessageContaining("靠近");
        verify(reservationService, never()).checkin(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("verifyLocation：范围内打卡成功，返回距离、半径与允许距离")
    void verifyLocation_success() {
        when(reservationService.detail(1L)).thenReturn(reservationOf(5L));
        when(resourceService.getById(5L)).thenReturn(resourceOf(5L, 1, 30.511442, 114.333556));
        Reservation done = reservationOf(5L);
        when(reservationService.checkin(eq(1L), eq(Reservation.CHECKIN_TYPE_LOCATION), anyString(),
                any(), any(), any(), any())).thenReturn(done);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) service.verifyLocation(1L, 30.511442, 114.333556, 20.0);

        assertThat(data.get("reservation")).isSameAs(done);
        assertThat(data.get("distance")).isEqualTo(0L);
        assertThat(data.get("accuracy")).isEqualTo(20L);
        assertThat(data.get("radius")).isEqualTo(120L);
        assertThat(data.get("allowedRadius")).isEqualTo(140L);
    }

    @Test
    @DisplayName("verifyLocation：体育馆使用 150 米半径，140 米处仍可打卡")
    void verifyLocation_gymUsesLargerRadius() {
        when(reservationService.detail(1L)).thenReturn(reservationOf(6L));
        when(resourceService.getById(6L)).thenReturn(resourceOf(6L, 4, 30.504850, 114.330324));
        when(reservationService.checkin(eq(1L), eq(Reservation.CHECKIN_TYPE_LOCATION), anyString(),
                any(), any(), any(), any())).thenReturn(reservationOf(6L));

        // 纬度 +0.0013 度 → 约 145 米，超出普通建筑半径但仍在体育馆半径内
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) service.verifyLocation(1L, 30.506150, 114.330324, 20.0);

        assertThat(data.get("radius")).isEqualTo(150L);
        assertThat((Long) data.get("distance")).isBetween(140L, 150L);
    }
}

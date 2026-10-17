package com.campus.reservation.service;

import com.campus.reservation.entity.CheckinCode;
import com.campus.reservation.mapper.CheckinCodeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CheckinCodeService 单元测试：当日验证码的查询与生成规则。
 * 重点：每个资源每天仅一条（存在则复用不再生成）、6 位数字、当日 23:59:59 过期。
 */
@ExtendWith(MockitoExtension.class)
class CheckinCodeServiceTest {

    @Mock
    private CheckinCodeMapper checkinCodeMapper;

    @InjectMocks
    private CheckinCodeService service;

    @Test
    @DisplayName("findTodayCode：resourceId 为 null → 直接返回 null，不查库")
    void findTodayCode_nullResourceId_returnsNull() {
        assertThat(service.findTodayCode(null)).isNull();
        verify(checkinCodeMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("ensureTodayCode：当日已有验证码 → 复用，不重复生成")
    void ensureTodayCode_reusesExisting() {
        CheckinCode existing = new CheckinCode();
        existing.setResourceId(1L);
        existing.setCode("123456");
        when(checkinCodeMapper.selectOne(any())).thenReturn(existing);

        CheckinCode result = service.ensureTodayCode(1L);

        assertThat(result).isSameAs(existing);
        verify(checkinCodeMapper, never()).insert(any(CheckinCode.class));
    }

    @Test
    @DisplayName("ensureTodayCode：当日无验证码 → 生成 6 位数字、当日 23:59:59 过期并入库")
    void ensureTodayCode_generatesNewCode() {
        when(checkinCodeMapper.selectOne(any())).thenReturn(null);

        CheckinCode result = service.ensureTodayCode(7L);

        assertThat(result.getCode()).matches("^\\d{6}$");
        assertThat(result.getResourceId()).isEqualTo(7L);
        assertThat(result.getValidDate()).isEqualTo(LocalDate.now());
        assertThat(result.getUsed()).isZero();
        assertThat(result.getExpiresAt()).isEqualTo(LocalDate.now().atTime(23, 59, 59));
        verify(checkinCodeMapper).insert(result);
    }

    @Test
    @DisplayName("ensureTodayCode：连续两次调用（第一次生成后第二次查到）→ 仅入库一条")
    void ensureTodayCode_onlyOnePerDay() {
        when(checkinCodeMapper.selectOne(any())).thenReturn(null);
        service.ensureTodayCode(1L);

        ArgumentCaptor<CheckinCode> captor = ArgumentCaptor.forClass(CheckinCode.class);
        verify(checkinCodeMapper).insert(captor.capture());
        CheckinCode saved = captor.getValue();

        // 模拟第二次调用：库中已存在当日记录
        when(checkinCodeMapper.selectOne(any())).thenReturn(saved);
        CheckinCode second = service.ensureTodayCode(1L);

        assertThat(second.getCode()).isEqualTo(saved.getCode());
        // 两次调用合计仍只入库一条
        verify(checkinCodeMapper, times(1)).insert(any(CheckinCode.class));
    }

    @Test
    @DisplayName("findTodayCode：查到的记录原样返回（不产生副作用）")
    void findTodayCode_returnsQueryResult() {
        CheckinCode today = new CheckinCode();
        today.setResourceId(1L);
        today.setCode("654321");
        today.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(checkinCodeMapper.selectOne(any())).thenReturn(today);

        CheckinCode result = service.findTodayCode(1L);

        assertThat(result.getCode()).isEqualTo("654321");
        verify(checkinCodeMapper, never()).insert(any(CheckinCode.class));
    }
}

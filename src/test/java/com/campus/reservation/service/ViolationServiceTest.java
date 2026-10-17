package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.dto.ViolationDTO;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.ViolationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ViolationService 单元测试：手动登记违规的校验分支、信用分扣减（含下限 0）、
 * 我的违规概览的周限制阈值判断。
 */
@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    @Mock
    private ViolationMapper violationMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private ViolationService service;

    private SysUser userWithCredit(int credit) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setCreditScore(credit);
        return u;
    }

    private ViolationDTO dto(Integer type, Integer deduct) {
        ViolationDTO dto = new ViolationDTO();
        dto.setUserId(1L);
        dto.setType(type);
        dto.setDeductScore(deduct);
        return dto;
    }

    // ---------------- add：参数与存在性校验 ----------------

    @Test
    @DisplayName("add：缺少 userId/type → 400")
    void add_missingParams_badRequest() {
        ViolationDTO dto = new ViolationDTO();
        dto.setUserId(null);
        dto.setType(1);
        assertThatThrownBy(() -> service.add(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);

        ViolationDTO dto2 = new ViolationDTO();
        dto2.setUserId(1L);
        dto2.setType(null);
        assertThatThrownBy(() -> service.add(dto2))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("add：用户不存在 → 404")
    void add_userNotFound() {
        when(userMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.add(dto(1, 10)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    // ---------------- add：信用分扣减 ----------------

    @Test
    @DisplayName("add：正常登记 → 违规入库 status=1，信用分 100-20=80")
    void add_success_deductsCredit() {
        when(userMapper.selectById(1L)).thenReturn(userWithCredit(100));

        Violation v = service.add(dto(1, 20));

        assertThat(v.getStatus()).isEqualTo(1);
        assertThat(v.getDeductScore()).isEqualTo(20);
        verify(violationMapper).insert(v);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCreditScore()).isEqualTo(80);
    }

    @Test
    @DisplayName("add：deductScore 为 null → 按 0 处理，信用分不变")
    void add_nullDeduct_treatedAsZero() {
        when(userMapper.selectById(1L)).thenReturn(userWithCredit(100));

        Violation v = service.add(dto(4, null));

        assertThat(v.getDeductScore()).isZero();
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCreditScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("add：扣分超过当前信用分 → 下限保护为 0，不出现负分")
    void add_deductBeyondCredit_floorsAtZero() {
        when(userMapper.selectById(1L)).thenReturn(userWithCredit(50));

        service.add(dto(2, 200));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCreditScore()).isZero();
    }

    // ---------------- mySummary：周限制阈值 ----------------

    @Test
    @DisplayName("mySummary：近 7 天违规达到阈值 → restricted=true")
    void mySummary_atLimit_restricted() {
        when(violationMapper.selectCount(any())).thenReturn(3L, 7L);

        Map<String, Object> m = service.mySummary(1L);

        assertThat(m.get("weekCount")).isEqualTo(3L);
        assertThat(m.get("totalCount")).isEqualTo(7L);
        assertThat(m.get("limit")).isEqualTo(ReservationService.WEEKLY_VIOLATION_LIMIT);
        assertThat(m.get("restricted")).isEqualTo(true);
    }

    @Test
    @DisplayName("mySummary：近 7 天违规未达阈值 → restricted=false")
    void mySummary_belowLimit_notRestricted() {
        when(violationMapper.selectCount(any())).thenReturn(2L, 5L);

        Map<String, Object> m = service.mySummary(1L);

        assertThat(m.get("restricted")).isEqualTo(false);
        assertThat(m.get("totalCount")).isEqualTo(5L);
    }
}

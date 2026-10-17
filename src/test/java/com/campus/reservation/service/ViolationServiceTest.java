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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ViolationService 单元测试：手动登记违规的校验分支（仅记录、不扣分）、
 * 我的违规概览的当日限制阈值判断。
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

    // ---------------- add：仅记录，不涉及信用分 ----------------

    @Test
    @DisplayName("add：正常登记 → 违规入库 status=1、deductScore=0，不更新用户分数")
    void add_success_recordsOnly() {
        when(userMapper.selectById(1L)).thenReturn(userWithCredit(100));

        Violation v = service.add(dto(1, 20));

        assertThat(v.getStatus()).isEqualTo(1);
        assertThat(v.getDeductScore()).isZero();
        verify(violationMapper).insert(v);
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("add：deductScore 为 null → 同样入库为 0")
    void add_nullDeduct_stillRecords() {
        when(userMapper.selectById(1L)).thenReturn(userWithCredit(100));

        Violation v = service.add(dto(4, null));

        assertThat(v.getDeductScore()).isZero();
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    // ---------------- mySummary：当日限制阈值 ----------------

    @Test
    @DisplayName("mySummary：今日违规达到阈值 → restricted=true")
    void mySummary_atLimit_restricted() {
        when(violationMapper.selectCount(any())).thenReturn(3L, 7L);

        Map<String, Object> m = service.mySummary(1L);

        assertThat(m.get("todayCount")).isEqualTo(3L);
        assertThat(m.get("totalCount")).isEqualTo(7L);
        assertThat(m.get("limit")).isEqualTo(ReservationService.DAILY_VIOLATION_LIMIT);
        assertThat(m.get("restricted")).isEqualTo(true);
    }

    @Test
    @DisplayName("mySummary：今日违规未达阈值 → restricted=false")
    void mySummary_belowLimit_notRestricted() {
        when(violationMapper.selectCount(any())).thenReturn(2L, 5L);

        Map<String, Object> m = service.mySummary(1L);

        assertThat(m.get("restricted")).isEqualTo(false);
        assertThat(m.get("totalCount")).isEqualTo(5L);
    }
}

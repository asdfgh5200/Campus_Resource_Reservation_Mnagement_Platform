package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.RepairDTO;
import com.campus.reservation.dto.RepairHandleDTO;
import com.campus.reservation.entity.RepairOrder;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.RepairOrderMapper;
import com.campus.reservation.mapper.ResourceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RepairService 单元测试：报修校验分支 + 管理员处理工单的状态机与资源状态联动。
 * 规则：确认维修(1)→资源置为维修中(3)；维修完成(2)→恢复可预约(1)；驳回(3)→资源状态不变。
 */
@ExtendWith(MockitoExtension.class)
class RepairServiceTest {

    @Mock
    private RepairOrderMapper repairOrderMapper;
    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private RepairService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private RepairOrder order(int status) {
        RepairOrder o = new RepairOrder();
        o.setId(1L);
        o.setResourceId(9L);
        o.setUserId(2L);
        o.setProblem("投影仪不亮");
        o.setStatus(status);
        return o;
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("create：缺少 resourceId/problem → 400")
    void create_missingParams_badRequest() {
        RepairDTO dto = new RepairDTO();
        dto.setResourceId(null);
        dto.setProblem("x");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);

        RepairDTO dto2 = new RepairDTO();
        dto2.setResourceId(1L);
        dto2.setProblem("  ");
        assertThatThrownBy(() -> service.create(dto2))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("create：资源不存在 → 404")
    void create_resourceNotFound() {
        when(resourceMapper.selectById(1L)).thenReturn(null);
        RepairDTO dto = new RepairDTO();
        dto.setResourceId(1L);
        dto.setProblem("空调坏了");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    @DisplayName("create：未登录 → 401")
    void create_notLoggedIn_unauthorized() {
        UserContext.clear();
        when(resourceMapper.selectById(1L)).thenReturn(new Resource());
        RepairDTO dto = new RepairDTO();
        dto.setResourceId(1L);
        dto.setProblem("空调坏了");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 401);
    }

    @Test
    @DisplayName("create：成功 → 工单为待处理，记录报修人并附带资源")
    void create_success() {
        UserContext.set(2L, 0);
        Resource resource = new Resource();
        resource.setId(1L);
        when(resourceMapper.selectById(1L)).thenReturn(resource);

        RepairDTO dto = new RepairDTO();
        dto.setResourceId(1L);
        dto.setProblem("投影仪不亮");
        dto.setImageUrl("http://img/1.png");

        RepairOrder order = service.create(dto);

        assertThat(order.getStatus()).isEqualTo(RepairOrder.STATUS_TODO);
        assertThat(order.getUserId()).isEqualTo(2L);
        assertThat(order.getProblem()).isEqualTo("投影仪不亮");
        assertThat(order.getResource()).isSameAs(resource);
        verify(repairOrderMapper).insert(order);
    }

    // ---------------- handle：状态机校验 ----------------

    @Test
    @DisplayName("handle：处理状态非法（null/0/4）→ 400")
    void handle_invalidStatus_badRequest() {
        for (Integer s : new Integer[]{null, 0, 4}) {
            RepairHandleDTO dto = new RepairHandleDTO();
            dto.setStatus(s);
            assertThatThrownBy(() -> service.handle(1L, dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", 400);
        }
    }

    @Test
    @DisplayName("handle：工单不存在 → 404")
    void handle_orderNotFound() {
        when(repairOrderMapper.selectById(1L)).thenReturn(null);
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_PROCESSING);
        assertThatThrownBy(() -> service.handle(1L, dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    @DisplayName("handle：工单已完结（已完成/已驳回）→ 400 不允许重复处理")
    void handle_alreadyFinished_badRequest() {
        UserContext.set(1L, 1);
        for (int finished : new int[]{RepairOrder.STATUS_DONE, RepairOrder.STATUS_REJECTED}) {
            when(repairOrderMapper.selectById(1L)).thenReturn(order(finished));
            RepairHandleDTO dto = new RepairHandleDTO();
            dto.setStatus(RepairOrder.STATUS_DONE);
            assertThatThrownBy(() -> service.handle(1L, dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", 400);
        }
    }

    // ---------------- handle：资源状态联动 ----------------

    @Test
    @DisplayName("handle：确认维修 → 工单处理中，资源置为维修中(3)")
    void handle_processing_setsResourceMaintaining() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_TODO));
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_PROCESSING);

        RepairOrder result = service.handle(1L, dto);

        assertThat(result.getStatus()).isEqualTo(RepairOrder.STATUS_PROCESSING);
        assertThat(result.getHandleUser()).isEqualTo(1L);
        assertThat(result.getHandleTime()).isNotNull();
        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getStatus()).isEqualTo(3);
    }

    @Test
    @DisplayName("handle：维修完成 → 资源恢复可预约(1)")
    void handle_done_restoresResource() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_PROCESSING));
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_DONE);

        service.handle(1L, dto);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("handle：驳回 → 只更新工单，资源状态不变")
    void handle_rejected_keepsResourceUntouched() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_TODO));
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_REJECTED);

        RepairOrder result = service.handle(1L, dto);

        assertThat(result.getStatus()).isEqualTo(RepairOrder.STATUS_REJECTED);
        verify(resourceMapper, never()).updateById(any(Resource.class));
    }
}

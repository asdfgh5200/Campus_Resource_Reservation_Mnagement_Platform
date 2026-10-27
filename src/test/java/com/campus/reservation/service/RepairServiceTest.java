package com.campus.reservation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.RepairDTO;
import com.campus.reservation.dto.RepairHandleDTO;
import com.campus.reservation.entity.RepairOrder;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.RepairOrderMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RepairService 单元测试：报修校验分支 + 管理员处理工单的状态机、资源状态联动与自动通知。
 * 规则：用户提交只建待审核工单（资源状态不变）；确认维修(1)→资源置为维修中(3)；
 * 维修完成(2)→恢复可预约(1)；驳回(3)→资源状态不变；每次处理都向报修人推送一条通知。
 */
@ExtendWith(MockitoExtension.class)
class RepairServiceTest {

    @Mock
    private RepairOrderMapper repairOrderMapper;
    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private MessageService messageService;

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
    @DisplayName("create：成功 → 工单为待审核，记录报修人并附带资源，且不改动资源状态")
    void create_success() {
        UserContext.set(2L, 0);
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setStatus(Resource.STATUS_AVAILABLE);
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
        // 提交只建工单，等管理员审核：资源状态保持不变（仍可预约）
        assertThat(resource.getStatus()).isEqualTo(Resource.STATUS_AVAILABLE);
        verify(resourceMapper, never()).updateById(any(Resource.class));
        verify(repairOrderMapper).insert(order);
        // 提交即回一条「已受理」通知，并带上地点便于跳转
        verify(messageService).notifyUser(eq(2L), anyString(), eq(1L));
    }

    // ---------------- myOrders：用户端「我的工单」 ----------------

    @Test
    @DisplayName("myOrders：未登录 → 401")
    void myOrders_notLoggedIn_unauthorized() {
        UserContext.clear();
        assertThatThrownBy(() -> service.myOrders())
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 401);
    }

    @Test
    @DisplayName("myOrders：按本人查询，并附带资源当前状态供跳转核对")
    void myOrders_success_attachesResource() {
        UserContext.set(2L, 0);
        when(repairOrderMapper.selectList(any())).thenReturn(List.of(order(RepairOrder.STATUS_TODO)));
        Resource resource = new Resource();
        resource.setId(9L);
        resource.setStatus(Resource.STATUS_REPORTED);
        when(resourceMapper.selectBatchIds(any())).thenReturn(List.of(resource));

        List<RepairOrder> list = service.myOrders();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getResource()).isSameAs(resource);
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

    // ---------------- handle：资源状态联动 + 自动通知 ----------------

    private Resource repairingResource() {
        Resource r = new Resource();
        r.setId(9L);
        r.setName("南湖校区 · 智能教室 3F-01");
        r.setStatus(Resource.STATUS_AVAILABLE);
        return r;
    }

    @Test
    @DisplayName("handle：确认维修 → 工单处理中，资源置为维修中(3)，并通知报修人")
    void handle_processing_setsResourceMaintaining() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_TODO));
        when(resourceMapper.selectById(9L)).thenReturn(repairingResource());
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
        // 处理结果自动推送，带上资源名与地点 ID
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageService).notifyUser(eq(2L), content.capture(), eq(9L));
        assertThat(content.getValue()).contains("维修中").contains("3F-01");
    }

    @Test
    @DisplayName("handle：维修完成 → 资源恢复可预约(1)，并通知报修人")
    void handle_done_restoresResource() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_PROCESSING));
        when(resourceMapper.selectById(9L)).thenReturn(repairingResource());
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_DONE);

        service.handle(1L, dto);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageService).notifyUser(eq(2L), content.capture(), eq(9L));
        assertThat(content.getValue()).contains("维修完成").contains("可预约");
    }

    @Test
    @DisplayName("handle：驳回 → 只更新工单，资源状态不变，驳回原因随通知发出")
    void handle_rejected_keepsResourceUntouched() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_TODO));
        when(resourceMapper.selectById(9L)).thenReturn(repairingResource());
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_REJECTED);
        dto.setReason("经核实为使用不当，设备正常");

        RepairOrder result = service.handle(1L, dto);

        assertThat(result.getStatus()).isEqualTo(RepairOrder.STATUS_REJECTED);
        assertThat(result.getRejectReason()).isEqualTo("经核实为使用不当，设备正常");
        verify(resourceMapper, never()).updateById(any(Resource.class));
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageService).notifyUser(eq(2L), content.capture(), eq(9L));
        assertThat(content.getValue()).contains("驳回").contains("设备正常");
    }

    @Test
    @DisplayName("handle：驳回未填原因 → 使用默认说明，通知不出现空白")
    void handle_rejected_blankReason_usesDefault() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_TODO));
        when(resourceMapper.selectById(9L)).thenReturn(repairingResource());
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_REJECTED);
        dto.setReason("  ");

        RepairOrder result = service.handle(1L, dto);

        assertThat(result.getRejectReason()).isNotBlank();
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageService).notifyUser(eq(2L), content.capture(), eq(9L));
        assertThat(content.getValue()).contains(result.getRejectReason());
    }

    @Test
    @DisplayName("handle：驳回原因超过 200 字 → 400，且不落库")
    void handle_rejected_reasonTooLong_badRequest() {
        UserContext.set(1L, 1);
        when(repairOrderMapper.selectById(1L)).thenReturn(order(RepairOrder.STATUS_TODO));
        RepairHandleDTO dto = new RepairHandleDTO();
        dto.setStatus(RepairOrder.STATUS_REJECTED);
        dto.setReason("废".repeat(201));

        assertThatThrownBy(() -> service.handle(1L, dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(repairOrderMapper, never()).updateById(any(RepairOrder.class));
        verify(messageService, never()).notifyUser(any(), anyString(), any());
    }

    // ---------------- page：管理端列表 ----------------

    @Test
    @DisplayName("page：列表补报修人姓名与学号，便于管理员核实回访")
    void page_fillsReporter() {
        UserContext.set(1L, 1);
        Page<RepairOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(order(RepairOrder.STATUS_TODO)));
        when(repairOrderMapper.selectPage(any(), any())).thenReturn(page);
        when(resourceMapper.selectBatchIds(any())).thenReturn(List.of());
        SysUser reporter = new SysUser();
        reporter.setId(2L);
        reporter.setUsername("20230001");
        reporter.setNickname("张三");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(reporter));

        Page<RepairOrder> result = service.page(null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getReporterName()).isEqualTo("张三");
        assertThat(result.getRecords().get(0).getReporterAccount()).isEqualTo("20230001");
    }
}

package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.AiToolRegistry;
import com.campus.reservation.entity.CampusClosure;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CampusClosureMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.service.ClosureService;
import com.campus.reservation.service.SystemConfigService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 管理员闭馆类 AI 工具测试：
 * 1) 一键闭馆/恢复、定向闭馆、撤销闭馆只产出草案，不直接改库；
 * 2) 参数校验失败时给出可自我纠正的错误，不产出草案；
 * 3) 四个工具都是管理员级，普通用户既看不到 schema 也执行不了。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminClosureToolsTest {

    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private CampusClosureMapper closureMapper;
    @Mock
    private ClosureService closureService;

    private AiToolContext adminCtx() {
        return new AiToolContext(1L, AiTool.ROLE_ADMIN, new ArrayList<>());
    }

    private AdminCampusClosureTool campusTool() {
        return new AdminCampusClosureTool(systemConfigService);
    }

    private AdminCreateClosureTool createTool() {
        return new AdminCreateClosureTool(resourceMapper);
    }

    private AdminRevokeClosureTool revokeTool() {
        return new AdminRevokeClosureTool(closureMapper);
    }

    private AdminListClosuresTool listTool() {
        return new AdminListClosuresTool(closureService, systemConfigService);
    }

    // ---------- 一键闭馆 ----------

    @Test
    @DisplayName("一键闭馆：当前开放 → 产出 campus_closure 草案，告知确认后才会生效")
    void campusClosure_createsDraft() throws Exception {
        when(systemConfigService.isClosed()).thenReturn(false);
        AiToolContext ctx = adminCtx();
        Map<String, Object> args = new HashMap<>();
        args.put("closed", true);
        args.put("reason", "台风停课");

        String reply = campusTool().execute(ctx, args);

        assertThat(reply).contains("draft_created");
        assertThat(ctx.getDrafts()).singleElement().satisfies(draft -> {
            assertThat(draft.get("kind")).isEqualTo("campus_closure");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) draft.get("payload");
            assertThat(payload.get("closed")).isEqualTo(true);
            assertThat(payload.get("reason")).isEqualTo("台风停课");
        });
    }

    @Test
    @DisplayName("一键闭馆：已处于闭馆状态 → noop，不重复产出草案")
    void campusClosure_noopWhenAlreadyClosed() throws Exception {
        when(systemConfigService.isClosed()).thenReturn(true);
        AiToolContext ctx = adminCtx();
        String reply = campusTool().execute(ctx, Map.of("closed", true));
        assertThat(reply).contains("noop");
        assertThat(ctx.getDrafts()).isEmpty();
    }

    @Test
    @DisplayName("一键闭馆：缺少 closed → 报错且不产出草案")
    void campusClosure_missingFlag() throws Exception {
        AiToolContext ctx = adminCtx();
        String reply = campusTool().execute(ctx, Map.of());
        assertThat(reply).contains("缺少 closed");
        assertThat(ctx.getDrafts()).isEmpty();
    }

    // ---------- 定向闭馆 ----------

    @Test
    @DisplayName("定向闭馆：整类闭馆 → 时刻补秒、范围文案为「全部图书馆」")
    void createClosure_byType_normalizesTime() throws Exception {
        AiToolContext ctx = adminCtx();
        String date = LocalDate.now().plusDays(1).toString();
        Map<String, Object> args = new HashMap<>();
        args.put("resourceType", 3);
        args.put("closureDate", date);
        args.put("startTime", "09:00");
        args.put("endTime", "11:30");
        args.put("reason", "设备检修");

        String reply = createTool().execute(ctx, args);

        assertThat(reply).contains("draft_created");
        assertThat(ctx.getDrafts()).singleElement().satisfies(draft -> {
            assertThat(draft.get("kind")).isEqualTo("create_closure");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) draft.get("payload");
            assertThat(payload.get("resourceType")).isEqualTo(3);
            assertThat(payload.get("resourceId")).isNull();
            assertThat(payload.get("startTime")).isEqualTo("09:00:00");
            assertThat(payload.get("endTime")).isEqualTo("11:30:00");
            assertThat(payload.get("scopeText")).isEqualTo("全部图书馆");
            assertThat(payload.get("closureDate")).isEqualTo(date);
        });
    }

    @Test
    @DisplayName("定向闭馆：指定具体资源 → 类型以资源为准，范围用资源名")
    void createClosure_byResource_usesRealType() throws Exception {
        Resource res = new Resource();
        res.setId(9L);
        res.setName("化学实验室");
        res.setResourceType(2);
        when(resourceMapper.selectById(9L)).thenReturn(res);

        AiToolContext ctx = adminCtx();
        Map<String, Object> args = new HashMap<>();
        args.put("resourceId", 9);
        args.put("closureDate", LocalDate.now().plusDays(2).toString());
        args.put("startTime", "14:00");
        args.put("endTime", "16:00");

        createTool().execute(ctx, args);

        assertThat(ctx.getDrafts()).singleElement().satisfies(draft -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) draft.get("payload");
            assertThat(payload.get("resourceId")).isEqualTo(9L);
            assertThat(payload.get("resourceType")).isEqualTo(2);
            assertThat(payload.get("scopeText")).isEqualTo("化学实验室");
        });
    }

    @Test
    @DisplayName("定向闭馆：目标是教室 → 明确拒绝，教室不参与预约")
    void createClosure_rejectsClassroom() throws Exception {
        Resource room = new Resource();
        room.setId(3L);
        room.setResourceType(1);
        when(resourceMapper.selectById(3L)).thenReturn(room);

        AiToolContext ctx = adminCtx();
        Map<String, Object> args = new HashMap<>();
        args.put("resourceId", 3);
        args.put("closureDate", LocalDate.now().plusDays(1).toString());
        args.put("startTime", "09:00");
        args.put("endTime", "10:00");

        String reply = createTool().execute(ctx, args);

        assertThat(reply).contains("教室");
        assertThat(ctx.getDrafts()).isEmpty();
    }

    @Test
    @DisplayName("定向闭馆：时段已过期 / 结束早于开始 → 报错不出草案")
    void createClosure_rejectsInvalidWindow() throws Exception {
        AiToolContext ctx = adminCtx();
        Map<String, Object> past = new HashMap<>();
        past.put("resourceType", 4);
        past.put("closureDate", LocalDate.now().minusDays(1).toString());
        past.put("startTime", "09:00");
        past.put("endTime", "10:00");
        assertThat(createTool().execute(ctx, past)).contains("已经过去");

        Map<String, Object> reversed = new HashMap<>();
        reversed.put("resourceType", 4);
        reversed.put("closureDate", LocalDate.now().plusDays(1).toString());
        reversed.put("startTime", "18:00");
        reversed.put("endTime", "09:00");
        assertThat(createTool().execute(ctx, reversed)).contains("必须晚于开始时刻");

        assertThat(ctx.getDrafts()).isEmpty();
    }

    // ---------- 撤销 / 查询 ----------

    @Test
    @DisplayName("撤销闭馆：窗口存在且生效 → 产出 revoke_closure 草案")
    void revokeClosure_createsDraft() throws Exception {
        CampusClosure closure = new CampusClosure();
        closure.setId(7L);
        closure.setResourceType(3);
        closure.setClosureDate(LocalDate.now());
        closure.setStartTime(LocalTime.of(9, 0));
        closure.setEndTime(LocalTime.of(12, 0));
        closure.setEnabled(CampusClosure.ENABLED);
        when(closureMapper.selectById(7L)).thenReturn(closure);

        AiToolContext ctx = adminCtx();
        revokeTool().execute(ctx, Map.of("closureId", 7));

        assertThat(ctx.getDrafts()).singleElement().satisfies(draft -> {
            assertThat(draft.get("kind")).isEqualTo("revoke_closure");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) draft.get("payload");
            assertThat(payload.get("closureId")).isEqualTo(7L);
            assertThat(payload.get("scopeText")).isEqualTo("全部图书馆");
            assertThat(String.valueOf(payload.get("windowText"))).contains("09:00~12:00");
        });
    }

    @Test
    @DisplayName("撤销闭馆：ID 不存在 → 报错不出草案")
    void revokeClosure_missing() throws Exception {
        when(closureMapper.selectById(any())).thenReturn(null);
        AiToolContext ctx = adminCtx();
        assertThat(revokeTool().execute(ctx, Map.of("closureId", 404))).contains("不存在");
        assertThat(ctx.getDrafts()).isEmpty();
    }

    @Test
    @DisplayName("查询闭馆：返回总体开关与生效中的窗口，供管理员核对")
    void listClosures_returnsStateAndWindows() throws Exception {
        CampusClosure closure = new CampusClosure();
        closure.setId(7L);
        closure.setResourceType(4);
        closure.setClosureDate(LocalDate.now());
        closure.setStartTime(LocalTime.of(20, 0));
        closure.setEndTime(LocalTime.of(22, 0));
        closure.setReason("比赛占用");
        when(closureService.listActive()).thenReturn(List.of(closure));
        when(systemConfigService.isClosed()).thenReturn(true);

        String reply = listTool().execute(adminCtx(), Map.of());

        assertThat(reply).contains("\"campusClosed\":true");
        assertThat(reply).contains("\"count\":1");
        assertThat(reply).contains("全部体育馆");
        assertThat(reply).contains("20:00");
    }

    // ---------- 权限门禁 ----------

    @Test
    @DisplayName("四个闭馆工具均为管理员级：普通用户看不到 schema 也执行不了")
    void closureTools_areAdminOnly() {
        List<AiTool> tools = List.of(campusTool(), createTool(), listTool(), revokeTool());
        tools.forEach(t -> assertThat(t.requiredRole()).isEqualTo(AiTool.ROLE_ADMIN));

        AiToolRegistry registry = new AiToolRegistry(tools);
        ArrayNode userSchemas = registry.schemasForRole(AiTool.ROLE_USER);
        assertThat(userSchemas).isEmpty();

        AiToolContext userCtx = new AiToolContext(10L, AiTool.ROLE_USER, new ArrayList<>());
        assertThat(registry.dispatch("admin_set_campus_closure", Map.of("closed", true), userCtx))
                .contains("无权限");
    }
}

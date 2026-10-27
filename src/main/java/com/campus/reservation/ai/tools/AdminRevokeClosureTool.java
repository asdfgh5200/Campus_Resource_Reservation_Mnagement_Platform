package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.entity.CampusClosure;
import com.campus.reservation.mapper.CampusClosureMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员「撤销定向闭馆」草案：只生成 draft，管理员确认后前端调用 DELETE /api/system/closures/{id}。
 * 撤销只把窗口置为不生效，不会恢复此前已被自动取消的预约。
 */
@Component
@RequiredArgsConstructor
public class AdminRevokeClosureTool implements AiTool {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final CampusClosureMapper closureMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "admin_revoke_closure";
    }

    @Override
    public String description() {
        return "撤销一条定向闭馆（只生成草案）：closureId 来自 admin_list_closures 返回的 id。"
                + "撤销后该时段恢复可预约，但此前已被自动取消的预约不会自动恢复。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("closureId", Map.of("type", "integer", "description", "闭馆记录 ID，来自 admin_list_closures"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of("closureId");
    }

    @Override
    public int requiredRole() {
        return ROLE_ADMIN;
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Long closureId = ToolUtils.longOrNull(args.get("closureId"));
        if (closureId == null) {
            return "{\"error\":\"缺少 closureId，请先调用 admin_list_closures 获取\"}";
        }
        CampusClosure closure = closureMapper.selectById(closureId);
        if (closure == null) {
            return "{\"error\":\"closureId=" + closureId + " 不存在，请先调用 admin_list_closures 获取真实 ID\"}";
        }
        if (closure.getEnabled() == null || closure.getEnabled() != CampusClosure.ENABLED) {
            return "{\"error\":\"该闭馆窗口已撤销，无需重复操作\"}";
        }
        String scopeText = closure.getResourceId() == null
                ? "全部" + typeLabel(closure.getResourceType())
                : (closure.getResourceName() == null ? "资源 #" + closure.getResourceId() : closure.getResourceName());
        String windowText = String.format("%s %s~%s",
                closure.getClosureDate(),
                closure.getStartTime() == null ? "—" : closure.getStartTime().format(TIME_FMT),
                closure.getEndTime() == null ? "—" : closure.getEndTime().format(TIME_FMT));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("closureId", closureId);
        payload.put("scopeText", scopeText);
        payload.put("windowText", windowText);
        ctx.getDrafts().add(Map.of("kind", "revoke_closure", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "撤销草案已生成（" + scopeText + " " + windowText + "），"
                        + "管理员在卡片点确认后才生效；已自动取消的预约不会恢复。"));
    }

    private static String typeLabel(Integer type) {
        if (type == null) return "资源";
        return switch (type) {
            case 2 -> "实验室";
            case 3 -> "图书馆";
            case 4 -> "体育馆";
            default -> "资源";
        };
    }
}

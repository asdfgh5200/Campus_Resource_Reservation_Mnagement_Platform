package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员「一键闭馆 / 恢复开放」草案。
 * 调用只产出 draft，管理员在前端确认卡片后才真正调用 /api/system/campus-close|open；
 * 闭馆会向全体用户广播，因此不允许模型直接生效。
 */
@Component
@RequiredArgsConstructor
public class AdminCampusClosureTool implements AiTool {

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "admin_set_campus_closure";
    }

    @Override
    public String description() {
        return "管理员一键闭馆/恢复开放（只生成草案，不会立即生效）。closed=true 表示开启一键闭馆：期间普通用户不能新建预约、"
                + "也不能使用资源，并会向全体用户广播闭馆通知；closed=false 表示恢复开放。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("closed", Map.of("type", "boolean", "description", "true=闭馆，false=恢复开放"));
        props.put("reason", Map.of("type", "string", "description", "闭馆原因，可省略，用于告知管理员与广播文案"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of("closed");
    }

    @Override
    public int requiredRole() {
        return ROLE_ADMIN;
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Boolean closed = ToolUtils.boolOrNull(args.get("closed"));
        if (closed == null) {
            return "{\"error\":\"缺少 closed，true 表示闭馆、false 表示恢复开放\"}";
        }
        String reason = ToolUtils.strOrNull(args.get("reason"));
        boolean current = systemConfigService.isClosed();
        if (current == closed) {
            return objectMapper.writeValueAsString(Map.of(
                    "status", "noop",
                    "note", closed ? "当前已经是闭馆状态，无需重复闭馆。" : "当前已经是开放状态，无需恢复。"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("closed", closed);
        payload.put("reason", reason);
        ctx.getDrafts().add(Map.of("kind", "campus_closure", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "已生成一键闭馆草案，管理员在卡片点确认后才生效；"
                        + "确认后系统会向全体用户广播闭馆通知，并提示管理员可在「闭馆管理」页恢复开放。"));
    }
}

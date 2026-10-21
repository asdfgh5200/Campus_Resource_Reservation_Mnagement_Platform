package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.ResourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成预约草案（不直接下单，前端确认后调用真实接口；普通用户级） */
@Component
@RequiredArgsConstructor
public class ProposeReservationTool implements AiTool {

    private final ResourceMapper resourceMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "propose_reservation";
    }

    @Override
    public String description() {
        return "生成一条预约草案（不会直接下单），前端展示确认卡片。时间格式必须为：yyyy-MM-dd HH:mm:ss（日期与时间之间为空格，不是 T）。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("resourceId", Map.of("type", "integer"));
        props.put("startTime", Map.of("type", "string", "description", "yyyy-MM-dd HH:mm:ss"));
        props.put("endTime", Map.of("type", "string", "description", "yyyy-MM-dd HH:mm:ss"));
        props.put("headcount", Map.of("type", "integer", "description", "使用人数，默认 1"));
        props.put("remark", Map.of("type", "string"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of("resourceId", "startTime", "endTime");
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Long resourceId = ToolUtils.longOrNull(args.get("resourceId"));
        String start = ToolUtils.strOrNull(args.get("startTime"));
        String end = ToolUtils.strOrNull(args.get("endTime"));
        Integer headcount = ToolUtils.intOrNull(args.get("headcount"));
        String remark = ToolUtils.strOrNull(args.get("remark"));
        if (resourceId == null || start == null || end == null) return "{\"error\":\"缺少 resourceId/startTime/endTime\"}";
        Resource res = resourceMapper.selectById(resourceId);
        if (res == null) {
            return "{\"error\":\"resourceId=" + resourceId + " 不存在，请先调用 query_resources 获取真实存在的资源\"}";
        }
        String normStart = ToolUtils.normalizeDateTime(start);
        String normEnd = ToolUtils.normalizeDateTime(end);
        if (normStart == null || normEnd == null) {
            return "{\"error\":\"时间格式不合法，请使用 yyyy-MM-dd HH:mm:ss\"}";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", resourceId);
        payload.put("startTime", normStart);
        payload.put("endTime", normEnd);
        payload.put("headcount", headcount == null ? 1 : headcount);
        payload.put("remark", remark);
        payload.put("resourceName", res.getName());
        ctx.getDrafts().add(Map.of("kind", "create_reservation", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "草案已生成并展示给用户，等待用户点击确认后才能真正下单。请简短告知用户下一步操作。"));
    }
}

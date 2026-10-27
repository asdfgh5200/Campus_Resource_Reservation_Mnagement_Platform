package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.ResourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员「定向闭馆」草案：整类资源闭馆，或只闭某个具体资源（某个球馆 / 某间实验室）。
 * 与真实接口一致——只作用于实验室(2)/图书馆(3)/体育馆(4)；教室不参与预约，不可闭馆。
 * 调用只产出 draft，管理员确认后由前端调用 POST /api/system/closures 真正生效。
 */
@Component
@RequiredArgsConstructor
public class AdminCreateClosureTool implements AiTool {

    /** 可闭馆的资源类型：2 实验室、3 图书馆、4 体育馆 */
    private static final List<Integer> CLOSABLE_TYPES = List.of(2, 3, 4);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ResourceMapper resourceMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "admin_create_closure";
    }

    @Override
    public String description() {
        return "管理员定向闭馆（只生成草案，不会立即生效）：可对整类资源闭馆（只传 resourceType），"
                + "也可只闭某个具体资源（先 query_resources 拿 resourceId 再传）。必须给出闭馆日期与起止时刻；"
                + "确认后系统会自动取消落在该时段的预约并通知预约者。仅支持实验室(2)/图书馆(3)/体育馆(4)。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("resourceType", Map.of("type", "integer", "description", "2 实验室、3 图书馆、4 体育馆；传 resourceId 时可省略"));
        props.put("resourceId", Map.of("type", "integer", "description", "只闭某个具体资源时传，整类闭馆不要传"));
        props.put("closureDate", Map.of("type", "string", "description", "闭馆日期 yyyy-MM-dd"));
        props.put("startTime", Map.of("type", "string", "description", "闭馆开始时刻 HH:mm"));
        props.put("endTime", Map.of("type", "string", "description", "闭馆结束时刻 HH:mm"));
        props.put("reason", Map.of("type", "string", "description", "闭馆原因，可省略"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of("closureDate", "startTime", "endTime");
    }

    @Override
    public int requiredRole() {
        return ROLE_ADMIN;
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Long resourceId = ToolUtils.longOrNull(args.get("resourceId"));
        Integer resourceType = ToolUtils.intOrNull(args.get("resourceType"));
        String dateText = ToolUtils.strOrNull(args.get("closureDate"));
        String startText = ToolUtils.strOrNull(args.get("startTime"));
        String endText = ToolUtils.strOrNull(args.get("endTime"));
        String reason = ToolUtils.strOrNull(args.get("reason"));

        if (dateText == null || startText == null || endText == null) {
            return "{\"error\":\"缺少 closureDate/startTime/endTime，日期格式 yyyy-MM-dd，时刻格式 HH:mm\"}";
        }
        LocalDate closureDate;
        try {
            closureDate = LocalDate.parse(dateText.trim());
        } catch (Exception e) {
            return "{\"error\":\"closureDate 格式不合法，应为 yyyy-MM-dd\"}";
        }
        LocalTime start = parseTime(startText);
        LocalTime end = parseTime(endText);
        if (start == null || end == null) {
            return "{\"error\":\"startTime/endTime 格式不合法，应为 HH:mm 或 HH:mm:ss\"}";
        }
        if (!start.isBefore(end)) {
            return "{\"error\":\"闭馆结束时刻必须晚于开始时刻\"}";
        }
        if (LocalDateTime.of(closureDate, end).isBefore(LocalDateTime.now())) {
            return "{\"error\":\"该闭馆时段已经过去，请给出尚未结束的日期与时段\"}";
        }

        // 指定具体资源时以资源真实类型为准，避免模型传错类型导致闭馆范围错位
        Resource target = null;
        if (resourceId != null) {
            target = resourceMapper.selectById(resourceId);
            if (target == null) {
                return "{\"error\":\"resourceId=" + resourceId + " 不存在，请先调用 query_resources 获取真实资源\"}";
            }
            if (target.getResourceType() == null || !CLOSABLE_TYPES.contains(target.getResourceType())) {
                return "{\"error\":\"只能对实验室/图书馆/体育馆闭馆，教室不参与预约、无法闭馆\"}";
            }
            resourceType = target.getResourceType();
        } else if (resourceType == null || !CLOSABLE_TYPES.contains(resourceType)) {
            return "{\"error\":\"请给出可闭馆的 resourceType：2 实验室、3 图书馆、4 体育馆\"}";
        }

        String scopeText = target != null ? target.getName() : "全部" + typeLabel(resourceType);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceType", resourceType);
        payload.put("resourceId", target == null ? null : target.getId());
        payload.put("resourceName", target == null ? null : target.getName());
        payload.put("closureDate", closureDate.toString());
        payload.put("startTime", start.format(TIME_FMT));
        payload.put("endTime", end.format(TIME_FMT));
        payload.put("reason", reason);
        payload.put("scopeText", scopeText);
        ctx.getDrafts().add(Map.of("kind", "create_closure", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "闭馆草案已生成（范围：" + scopeText + "，" + closureDate + " "
                        + start.format(TIME_FMT) + "~" + end.format(TIME_FMT) + "），"
                        + "管理员在卡片点确认后才生效；确认后落在该时段的预约会被自动取消并通知预约者。"));
    }

    /** 兼容 HH:mm 与 HH:mm:ss 两种模型输出 */
    private static LocalTime parseTime(String raw) {
        String s = raw.trim();
        try {
            return s.length() == 5 ? LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")) : LocalTime.parse(s);
        } catch (Exception e) {
            return null;
        }
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

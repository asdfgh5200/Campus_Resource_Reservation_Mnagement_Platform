package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.config.DeepSeekProperties;
import com.campus.reservation.dto.AiChatRequest;
import com.campus.reservation.dto.AiChatReply;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智约机器人：调用 DeepSeek（OpenAI 兼容 Chat Completions + tool/function calling）。
 * 只读工具（查询资源、查询空闲、我的预约）在服务端直接执行并回填结果；
 * 写操作工具（propose_reservation / propose_cancel）只返回"草案"给前端，用户点确认后才调用真实接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是“智约”，校园资源预约平台的 AI 助手。请用中文简洁回答。

            当前时间：今天={today}（{todayWeekday}），现在时刻={nowTime}；一周为周一至周日。
            用户提到“今天/明天/后天/大后天/下周”时，**直接目算为具体日期**后写入工具参数，绝不要反问“你说的是几号”！

            你可以调用以下工具（无参数就直接调用，不要推演戒词）：
            - query_resources：按类型/关键词搜索可用资源；type=1 教室、2 实验室、3 图书馆、4 体育馆。
            - get_busy_slots：查某资源某日已占用时段（返回 busySlots=[{{start,end}}]）。
            - list_my_reservations：当前用户的预约列表。
            - propose_reservation：生成预约草案（前端会弹确认卡片，你无需担心写风险，大胆调用）。
            - propose_cancel：生成取消草案。

            预约规则：
            1) 同一时段每人只能一个预约；
            2) 实验室/体育馆最多提前 7 天，图书馆最多提前 1 天（即“明天”），教室不需预约；
            3) 开放时间内才能约：图书馆 22:00、体育馆 21:00、实验室 20:00；
            4) 预约后 30 分钟内验证码打卡，否则爽约；1 天内违规 3 次当日禁约。

            【行为准则】：
            • 任何关于具体资源名称/数量/可用时段的描述，**必须先调工具查询**；不要自己编造“一共有10个座位”“6F多媒体室”这类不存在于查询结果里的信息。
            • 当用户已给出“地点类型 + 时间”（如“明天下午3–5点图书馆”），不要反复确认日期、时长、人数，直接：
               1) query_resources 拿到候选 resourceId；
               2) get_busy_slots 确认目标时段不冲突；
               3) propose_reservation 直接生成草案，写入 startTime/endTime（格式必须为 yyyy-MM-dd HH:mm:ss，中间为空格不是 T），默认 headcount=1。
              若同时段已被占用，自动向前后扩展 30 分钟选一个接近的空闲时段，并在回复里告知你做了什么调整。
            • propose_reservation 不会直接下单，不会造成错约，大胆调用。卡片会交给用户确认。
            • 回复时不要复述平台规则（“图书馆最多预约到次日”等已知常识），除非用户间到规则本身。
            • 若真的缺少关键信息（如完全不知道地点或时间），一次把要问的都问了，不要分多轮。
            """
            ;

    private static final String[] WEEKDAY_CN = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final DeepSeekProperties props;
    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AiChatReply chat(AiChatRequest req) {
        if (!props.ready()) {
            throw new BizException(503, "AI 机器人未启用：请在部署环境配置 DEEPSEEK_API_KEY");
        }
        if (req == null || req.getMessages() == null || req.getMessages().isEmpty()) {
            throw BizException.badRequest("messages 不能为空");
        }
        Long userId = UserContext.requireUserId();

        ArrayNode messages = new ArrayNode(JsonNodeFactory.instance);
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", buildSystemPrompt());
        for (AiChatRequest.Message m : req.getMessages()) {
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            ObjectNode mm = messages.addObject();
            mm.put("role", "user".equals(m.getRole()) ? "user" : "assistant");
            mm.put("content", m.getContent());
        }

        List<Map<String, Object>> drafts = new ArrayList<>();
        String finalContent = null;

        try {
            for (int loop = 0; loop < props.getMaxToolLoops(); loop++) {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("model", props.getModel());
                body.set("messages", messages);
                body.put("tool_choice", "auto");
                body.set("tools", toolSchemas());

                JsonNode resp = callLlm(body);
                JsonNode choice = resp.path("choices").path(0).path("message");
                String content = choice.path("content").isMissingNode() || choice.path("content").isNull()
                        ? null : choice.path("content").asText();
                JsonNode toolCalls = choice.path("tool_calls");

                if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                    finalContent = content;
                    break;
                }
                // 有 tool_calls：把 assistant 消息（含 tool_calls）塞回历史，然后逐条执行工具
                messages.add(deepCopy(choice));
                for (JsonNode tc : toolCalls) {
                    String id = tc.path("id").asText();
                    String name = tc.path("function").path("name").asText();
                    String argsStr = tc.path("function").path("arguments").asText("{}");
                    Map<String, Object> args = parseArgs(argsStr);
                    String observation = executeTool(name, args, userId, drafts);
                    ObjectNode toolMsg = messages.addObject();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", id);
                    toolMsg.put("name", name);
                    toolMsg.put("content", observation);
                }
                // 若最后一轮未再触发 tool_calls 则下一轮 LLM 会给出最终 content
                if (loop == props.getMaxToolLoops() - 1) {
                    finalContent = content == null ? "抱歉，我暂时处理不了这个请求，请稍后再试。" : content;
                }
            }
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 会话失败", e);
            throw new BizException(502, "AI 服务调用失败：" + e.getMessage());
        }

        AiChatReply reply = new AiChatReply();
        reply.setContent(finalContent == null ? "我已经收到，请查看下方的操作卡片。" : finalContent);
        reply.setPendingActions(drafts);
        AiChatReply.Message echo = new AiChatReply.Message();
        echo.setRole("assistant");
        echo.setContent(reply.getContent());
        reply.setAssistantEcho(echo);
        return reply;
    }

    // ---------------- 工具执行 ----------------

    private String buildSystemPrompt() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        return SYSTEM_PROMPT_TEMPLATE
                .replace("{today}", today.toString())
                .replace("{todayWeekday}", WEEKDAY_CN[today.getDayOfWeek().getValue() - 1])
                .replace("{nowTime}", String.format("%02d:%02d", now.getHour(), now.getMinute()));
    }

    private String executeTool(String name, Map<String, Object> args, Long userId, List<Map<String, Object>> drafts) {
        try {
            return switch (name) {
                case "query_resources" -> queryResources(args);
                case "get_busy_slots" -> getBusySlots(args);
                case "list_my_reservations" -> listMyReservations(userId, args);
                case "propose_reservation" -> proposeReservation(args, drafts);
                case "propose_cancel" -> proposeCancel(args, drafts);
                default -> "{\"error\":\"未知工具 " + name + "\"}";
            };
        } catch (Exception e) {
            log.warn("工具 {} 执行异常", name, e);
            return "{\"error\":\"" + escape(e.getMessage()) + "\"}";
        }
    }

    private String queryResources(Map<String, Object> args) throws Exception {
        Integer type = intOrNull(args.get("type"));
        String keyword = strOrNull(args.get("keyword"));
        List<Resource> list = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .eq(type != null, Resource::getResourceType, type)
                .like(keyword != null, Resource::getName, keyword)
                .eq(Resource::getStatus, 1)
                .last("LIMIT 10"));
        List<Map<String, Object>> slim = list.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("name", r.getName());
            m.put("type", r.getResourceType());
            m.put("building", r.getBuilding());
            m.put("openTime", String.valueOf(r.getOpenTime()));
            m.put("closeTime", String.valueOf(r.getCloseTime()));
            m.put("capacity", r.getCapacity());
            return m;
        }).toList();
        return objectMapper.writeValueAsString(Map.of("resources", slim));
    }

    private String getBusySlots(Map<String, Object> args) throws Exception {
        Long resourceId = longOrNull(args.get("resourceId"));
        String dateStr = strOrNull(args.get("date"));
        if (resourceId == null || dateStr == null) return "{\"error\":\"缺少 resourceId 或 date\"}";
        LocalDate d = LocalDate.parse(dateStr);
        LocalDateTime ds = d.atStartOfDay();
        LocalDateTime de = d.plusDays(1).atStartOfDay();
        List<Reservation> rs = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getResourceId, resourceId)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, de)
                .gt(Reservation::getEndTime, ds));
        List<Map<String, String>> busy = rs.stream().map(r -> Map.of(
                "start", r.getStartTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                "end", r.getEndTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))).toList();
        return objectMapper.writeValueAsString(Map.of("date", dateStr, "busySlots", busy));
    }

    private String listMyReservations(Long userId, Map<String, Object> args) throws Exception {
        Integer status = intOrNull(args.get("status"));
        List<Reservation> rs = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, userId)
                .eq(status != null, Reservation::getStatus, status)
                .orderByDesc(Reservation::getStartTime)
                .last("LIMIT 10"));
        List<Map<String, Object>> list = rs.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("resourceId", r.getResourceId());
            Resource res = r.getResourceId() == null ? null : resourceMapper.selectById(r.getResourceId());
            m.put("resourceName", res == null ? null : res.getName());
            m.put("start", String.valueOf(r.getStartTime()));
            m.put("end", String.valueOf(r.getEndTime()));
            m.put("status", r.getStatus());
            return m;
        }).toList();
        return objectMapper.writeValueAsString(Map.of("reservations", list));
    }

    private String proposeReservation(Map<String, Object> args, List<Map<String, Object>> drafts) throws Exception {
        Long resourceId = longOrNull(args.get("resourceId"));
        String start = strOrNull(args.get("startTime"));
        String end = strOrNull(args.get("endTime"));
        Integer headcount = intOrNull(args.get("headcount"));
        String remark = strOrNull(args.get("remark"));
        if (resourceId == null || start == null || end == null) return "{\"error\":\"缺少 resourceId/startTime/endTime\"}";
        Resource res = resourceMapper.selectById(resourceId);
        if (res == null) {
            return "{\"error\":\"resourceId=" + resourceId + " 不存在，请先调用 query_resources 获取真实存在的资源\"}";
        }
        String normStart = normalizeDateTime(start);
        String normEnd = normalizeDateTime(end);
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
        drafts.add(Map.of("kind", "create_reservation", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "草案已生成并展示给用户，等待用户点击确认后才能真正下单。请简短告知用户下一步操作。"));
    }

    private String proposeCancel(Map<String, Object> args, List<Map<String, Object>> drafts) throws Exception {
        Long reservationId = longOrNull(args.get("reservationId"));
        if (reservationId == null) return "{\"error\":\"缺少 reservationId\"}";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", reservationId);
        drafts.add(Map.of("kind", "cancel_reservation", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "取消草案已生成，等待用户在卡片上确认。"));
    }

    // ---------------- HTTP & Schema ----------------

    private JsonNode callLlm(ObjectNode body) throws Exception {
        String url = props.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new BizException(502, "LLM 返回 " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        return objectMapper.readTree(resp.body());
    }

    private ArrayNode toolSchemas() {
        ArrayNode arr = new ArrayNode(JsonNodeFactory.instance);
        arr.add(toolSchema("query_resources",
                "按类型和关键词查询可用资源。type=1 教室 / 2 实验室 / 3 图书馆 / 4 体育馆；keyword 可选（资源名模糊匹配）。",
                Map.of(
                        "type", Map.of("type", "integer", "enum", List.of(1, 2, 3, 4), "description", "资源类型"),
                        "keyword", Map.of("type", "string", "description", "资源名称关键词")
                ), List.of()));
        arr.add(toolSchema("get_busy_slots",
                "查询指定资源某日已被占用的时段。date 形如 2026-10-18。",
                Map.of(
                        "resourceId", Map.of("type", "integer", "description", "资源 ID"),
                        "date", Map.of("type", "string", "description", "日期 yyyy-MM-dd")
                ), List.of("resourceId", "date")));
        arr.add(toolSchema("list_my_reservations",
                "查询当前用户最近的预约（最多 10 条）。status 可选：1 待打卡 / 2 使用中 / 3 已完成 / 4 已取消 / 5 爽约。",
                Map.of(
                        "status", Map.of("type", "integer", "description", "可选状态过滤")
                ), List.of()));
        arr.add(toolSchema("propose_reservation",
                "生成一条预约草案（不会直接下单），前端展示确认卡片。时间格式必须为：yyyy-MM-dd HH:mm:ss（日期与时间之间为空格，不是 T）。",
                Map.of(
                        "resourceId", Map.of("type", "integer"),
                        "startTime", Map.of("type", "string", "description", "yyyy-MM-dd HH:mm:ss"),
                        "endTime", Map.of("type", "string", "description", "yyyy-MM-dd HH:mm:ss"),
                        "headcount", Map.of("type", "integer", "description", "使用人数，默认 1"),
                        "remark", Map.of("type", "string")
                ), List.of("resourceId", "startTime", "endTime")));
        arr.add(toolSchema("propose_cancel",
                "生成一条取消预约草案（不会真正取消），前端展示确认卡片。仅对 status=1 待打卡的预约有效。",
                Map.of(
                        "reservationId", Map.of("type", "integer")
                ), List.of("reservationId")));
        return arr;
    }

    private ObjectNode toolSchema(String name, String desc, Map<String, Object> propsMap, List<String> required) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "function");
        ObjectNode fn = node.putObject("function");
        fn.put("name", name);
        fn.put("description", desc);
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        ObjectNode p = params.putObject("properties");
        propsMap.forEach((k, v) -> {
            try {
                p.set(k, objectMapper.valueToTree(v));
            } catch (Exception ignore) { /* 忽略 */ }
        });
        if (!required.isEmpty()) {
            ArrayNode req = params.putArray("required");
            required.forEach(req::add);
        }
        return node;
    }

    // ---------------- 小工具 ----------------

    private static Integer intOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private static Long longOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private static String strOrNull(Object v) {
        return v == null ? null : v.toString();
    }

    /**
     * 将 LLM 可能输出的 ISO / 缺秒 / 带 T 的时间字符串归一化为项目统一的 yyyy-MM-dd HH:mm:ss。
     * 无法解析时返回 null。
     */
    private static String normalizeDateTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace('T', ' ');
        // 去掉可能带的毫秒 / 时区后缀
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        int plus = s.indexOf('+');
        if (plus > 0) s = s.substring(0, plus);
        if (s.length() == 16) s = s + ":00";   // yyyy-MM-dd HH:mm → 补秒
        if (s.length() != 19) return null;
        try {
            LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    private Map<String, Object> parseArgs(String json) throws Exception {
        if (json == null || json.isBlank()) return Map.of();
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private JsonNode deepCopy(JsonNode n) {
        return n.deepCopy();
    }

    // JsonNode 工厂（避免引入 jackson-node 依赖路径差异）
    private static final class JsonNodeFactory {
        static final com.fasterxml.jackson.databind.node.JsonNodeFactory instance
                = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
    }

    // 未使用的辅助，避免 IDE 报"unused import"
    @SuppressWarnings("unused")
    private static LocalTime _ltPlaceholder() { return null; }
}

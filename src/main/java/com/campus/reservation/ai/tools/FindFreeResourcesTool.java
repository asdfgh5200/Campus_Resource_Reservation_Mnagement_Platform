package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.common.BizException;
import com.campus.reservation.service.AvailabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按日期时段查真正可用的资源（只读，普通用户级） */
@Component
@RequiredArgsConstructor
public class FindFreeResourcesTool implements AiTool {

    /** 单次返回上限：够模型挑选，又不至于把上下文撑爆 */
    private static final int MAX_RESULT = 15;

    private final AvailabilityService availabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "find_free_resources";
    }

    @Override
    public String description() {
        return "按日期+时段筛选真正可用的资源：已排除正在上课、已被他人预约、闭馆、维修中的资源，"
                + "体育馆按剩余容量判断。生成预约草案前应先用它确认时段可用，不要凭印象推荐。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("date", Map.of("type", "string", "description", "日期 yyyy-MM-dd"));
        props.put("start", Map.of("type", "string", "description", "开始时刻 HH:mm，可省略（默认现在向上取整到半点）"));
        props.put("end", Map.of("type", "string", "description", "结束时刻 HH:mm，可省略（默认开始后 2 小时）"));
        props.put("type", Map.of("type", "integer", "enum", List.of(1, 2, 3, 4),
                "description", "资源类型：1 教室 / 2 实验室 / 3 图书馆 / 4 体育馆；可省略表示四类都查"));
        props.put("campus", Map.of("type", "string", "description", "校区，可省略"));
        props.put("building", Map.of("type", "string", "description", "楼栋/运动场，可省略"));
        props.put("roomType", Map.of("type", "string", "description", "实验方向/运动类型等细分类型，可省略"));
        props.put("keyword", Map.of("type", "string", "description", "房间号或名称关键词，可省略"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of("date");
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        String dateStr = ToolUtils.strOrNull(args.get("date"));
        if (dateStr == null) {
            return "{\"error\":\"缺少 date（yyyy-MM-dd）\"}";
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            return "{\"error\":\"date 格式不合法，请使用 yyyy-MM-dd\"}";
        }
        LocalTime start = parseTime(ToolUtils.strOrNull(args.get("start")));
        LocalTime end = parseTime(ToolUtils.strOrNull(args.get("end")));
        if (start != null && end != null && !start.isBefore(end)) {
            return "{\"error\":\"end 必须晚于 start\"}";
        }
        Integer type = ToolUtils.intOrNull(args.get("type"));
        String campus = ToolUtils.strOrNull(args.get("campus"));
        String building = ToolUtils.strOrNull(args.get("building"));
        String roomType = ToolUtils.strOrNull(args.get("roomType"));
        String keyword = ToolUtils.strOrNull(args.get("keyword"));
        try {
            List<Map<String, Object>> list = availabilityService.freeResources(date, start, end, type, campus,
                    building == null ? null : List.of(building),
                    roomType == null ? null : List.of(roomType),
                    null, keyword);
            List<Map<String, Object>> slim = list.stream().limit(MAX_RESULT).toList();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("date", date.toString());
            out.put("total", list.size());
            out.put("freeResources", slim);
            if (list.size() > slim.size()) {
                out.put("note", "仅返回前 " + slim.size() + " 个，可加 campus/building/roomType/keyword 收窄范围");
            }
            if (list.isEmpty()) {
                out.put("note", "该时段没有可用资源，请换时间或换地点后再查，不要把已占用的资源推荐给用户");
            }
            return objectMapper.writeValueAsString(out);
        } catch (BizException e) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("error", e.getMessage());
            out.put("freeResources", List.of());
            return objectMapper.writeValueAsString(out);
        }
    }

    /** 兼容 9:00 / 09:00 两种写法的时刻解析 */
    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            int h = Integer.parseInt(s.substring(0, colon).trim());
            int m = Integer.parseInt(s.substring(colon + 1).trim());
            return LocalTime.of(h, m);
        } catch (Exception e) {
            return null;
        }
    }
}

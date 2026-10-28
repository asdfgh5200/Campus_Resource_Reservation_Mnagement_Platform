package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.service.AvailabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询某资源某日的占用情况（只读，普通用户级）。
 *
 * <p>占用不完全等于「有人预约」：课程排课、闭馆同样让资源不可用，
 * 所以这里统一走 {@link AvailabilityService#daySchedule}，直接把课程/预约/闭馆一起列出来，
 * 避免模型只查预约就认定「这段时间是空的」。
 */
@Component
@RequiredArgsConstructor
public class GetBusySlotsTool implements AiTool {

    private final ResourceMapper resourceMapper;
    private final AvailabilityService availabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "get_busy_slots";
    }

    @Override
    public String description() {
        return "查询指定资源某日的占用与空闲：busySlots 含上课/已被预约/闭馆（kind 区分），freeSlots 为开放时间内可约的窗口。date 形如 2026-10-18。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("resourceId", Map.of("type", "integer", "description", "资源 ID"));
        props.put("date", Map.of("type", "string", "description", "日期 yyyy-MM-dd"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of("resourceId", "date");
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Long resourceId = ToolUtils.longOrNull(args.get("resourceId"));
        String dateStr = ToolUtils.strOrNull(args.get("date"));
        if (resourceId == null || dateStr == null) {
            return "{\"error\":\"缺少 resourceId 或 date\"}";
        }
        LocalDate day;
        try {
            day = LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            return "{\"error\":\"date 格式不合法，请使用 yyyy-MM-dd\"}";
        }
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            return "{\"error\":\"resourceId=" + resourceId + " 不存在，请先调用 query_resources 获取真实存在的资源\"}";
        }
        return objectMapper.writeValueAsString(availabilityService.daySchedule(resource, day));
    }
}

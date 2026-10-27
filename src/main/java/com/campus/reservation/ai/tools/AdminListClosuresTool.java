package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.entity.CampusClosure;
import com.campus.reservation.service.ClosureService;
import com.campus.reservation.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员闭馆现状查询（只读，立即返回）：
 * 总体一键闭馆开关状态 + 所有生效中的定向闭馆窗口，供"我现在闭馆了吗/有哪些闭馆安排"这类问题使用。
 */
@Component
@RequiredArgsConstructor
public class AdminListClosuresTool implements AiTool {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ClosureService closureService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "admin_list_closures";
    }

    @Override
    public String description() {
        return "查询当前闭馆现状（只读）：总体一键闭馆是否开启，以及所有生效中的定向闭馆窗口（含闭馆 ID、范围、日期时段、原因）。"
                + "撤销闭馆前先用本工具拿到闭馆 ID。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of();
    }

    @Override
    public List<String> requiredParams() {
        return List.of();
    }

    @Override
    public int requiredRole() {
        return ROLE_ADMIN;
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (CampusClosure c : closureService.listActive()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("scope", c.getResourceId() == null
                    ? "全部" + typeLabel(c.getResourceType())
                    : (c.getResourceName() == null ? "资源 #" + c.getResourceId() : c.getResourceName()));
            item.put("resourceType", c.getResourceType());
            item.put("resourceId", c.getResourceId());
            item.put("closureDate", c.getClosureDate() == null ? null : c.getClosureDate().toString());
            item.put("startTime", c.getStartTime() == null ? null : c.getStartTime().format(TIME_FMT));
            item.put("endTime", c.getEndTime() == null ? null : c.getEndTime().format(TIME_FMT));
            item.put("reason", c.getReason());
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("campusClosed", systemConfigService.isClosed());
        body.put("count", items.size());
        body.put("closures", items);
        body.put("note", items.isEmpty()
                ? "当前没有生效中的定向闭馆窗口。"
                : "以上是当前生效中的闭馆窗口，撤销时把 id 传给 admin_revoke_closure。");
        return objectMapper.writeValueAsString(body);
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

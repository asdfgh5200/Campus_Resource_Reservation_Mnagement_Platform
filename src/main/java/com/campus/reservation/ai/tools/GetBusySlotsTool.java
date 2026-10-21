package com.campus.reservation.ai.tools;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.mapper.ReservationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询某资源某日已占用时段（只读，普通用户级） */
@Component
@RequiredArgsConstructor
public class GetBusySlotsTool implements AiTool {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationMapper reservationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "get_busy_slots";
    }

    @Override
    public String description() {
        return "查询指定资源某日已被占用的时段。date 形如 2026-10-18。";
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
                "start", r.getStartTime().toLocalTime().format(HHMM),
                "end", r.getEndTime().toLocalTime().format(HHMM))).toList();
        return objectMapper.writeValueAsString(Map.of("date", dateStr, "busySlots", busy));
    }
}

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成预约草案（不直接下单，前端确认后调用真实接口；普通用户级）。
 *
 * <p>生成前先走 {@link AvailabilityService#check} 做一次真实可用性判定：
 * 被课程占用、已被他人预约、闭馆、超出开放时间的时段一律不出草案，
 * 避免模型把已经占用的时段包装成“推荐方案”丢给用户去确认。
 */
@Component
@RequiredArgsConstructor
public class ProposeReservationTool implements AiTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ResourceMapper resourceMapper;
    private final AvailabilityService availabilityService;
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
        if (res.getResourceType() != null && res.getResourceType() == 1) {
            return blocked("教室无需预约，未被课程占用时可直接使用");
        }
        int people = headcount == null || headcount < 1 ? 1 : headcount;
        AvailabilityService.Availability availability = availabilityService.check(res,
                LocalDateTime.parse(normStart, FMT), LocalDateTime.parse(normEnd, FMT));
        if (!availability.available()) {
            return blocked(availability.reason());
        }
        if (res.getResourceType() != null && res.getResourceType() == 4 && people > availability.remaining()) {
            return blocked(String.format("该时段只剩 %d %s，不够 %d 人使用",
                    availability.remaining(), res.getUnit() == null ? "人" : res.getUnit(), people));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", resourceId);
        payload.put("startTime", normStart);
        payload.put("endTime", normEnd);
        payload.put("headcount", people);
        payload.put("remark", remark);
        payload.put("resourceName", res.getName());
        ctx.getDrafts().add(Map.of("kind", "create_reservation", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "该时段已校验可用，草案已展示给用户，等待用户点击确认后才能真正下单。请简短告知用户下一步操作。"));
    }

    /** 时段不可用：不出草案，并把“怎么改”告诉模型，避免它换个借口继续推荐同一时段 */
    private String blocked(String reason) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "blocked");
        out.put("error", "该时段不可预约：" + reason);
        out.put("hint", "不要把这个时段推给用户。可调用 get_busy_slots 看 freeSlots，"
                + "或调用 find_free_resources 换一个真正空闲的时段/资源后重新生成草案。");
        return objectMapper.writeValueAsString(out);
    }
}

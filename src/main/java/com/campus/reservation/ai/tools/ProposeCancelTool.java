package com.campus.reservation.ai.tools;

import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.mapper.ReservationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成取消预约草案（不直接取消，前端确认后调用真实接口；普通用户级，仅限本人预约） */
@Component
@RequiredArgsConstructor
public class ProposeCancelTool implements AiTool {

    private final ReservationMapper reservationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "propose_cancel";
    }

    @Override
    public String description() {
        return "生成一条取消预约草案（不会真正取消），前端展示确认卡片。仅对 status=1 待打卡的预约有效。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("reservationId", Map.of("type", "integer"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of("reservationId");
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Long reservationId = ToolUtils.longOrNull(args.get("reservationId"));
        if (reservationId == null) return "{\"error\":\"缺少 reservationId\"}";
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) {
            return "{\"error\":\"reservationId=" + reservationId + " 不存在，请先调用 list_my_reservations 获取真实 ID\"}";
        }
        // 权限限制：只能取消本人的预约，普通用户不可借草案操作他人预约
        if (!r.getUserId().equals(ctx.getUserId())) {
            return "{\"error\":\"无权操作他人预约，仅可取消本人预约\"}";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", reservationId);
        ctx.getDrafts().add(Map.of("kind", "cancel_reservation", "payload", payload));
        return objectMapper.writeValueAsString(Map.of(
                "status", "draft_created",
                "note", "取消草案已生成，等待用户在卡片上确认。"));
    }
}

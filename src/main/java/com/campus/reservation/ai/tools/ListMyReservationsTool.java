package com.campus.reservation.ai.tools;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.ToolUtils;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询当前登录用户自己的预约（只读，普通用户级；只能看本人，忽略任何越权参数） */
@Component
@RequiredArgsConstructor
public class ListMyReservationsTool implements AiTool {

    private final ReservationMapper reservationMapper;
    private final ResourceMapper resourceMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "list_my_reservations";
    }

    @Override
    public String description() {
        return "查询当前用户最近的预约（最多 10 条）。status 可选：1 待打卡 / 2 使用中 / 3 已完成 / 4 已取消 / 5 爽约。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("status", Map.of("type", "integer", "description", "可选状态过滤"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of();
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Integer status = ToolUtils.intOrNull(args.get("status"));
        // 强制以登录用户身份查询，杜绝通过参数查看他人预约
        List<Reservation> rs = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, ctx.getUserId())
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
}

package com.campus.reservation.ai.tools;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

/** 按类型/关键词查询可用资源（只读，普通用户级） */
@Component
@RequiredArgsConstructor
public class QueryResourcesTool implements AiTool {

    private final ResourceMapper resourceMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "query_resources";
    }

    @Override
    public String description() {
        return "按类型和关键词查询可用资源。type=1 教室 / 2 实验室 / 3 图书馆 / 4 体育馆；keyword 可选（资源名模糊匹配）。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", Map.of("type", "integer", "enum", List.of(1, 2, 3, 4), "description", "资源类型"));
        props.put("keyword", Map.of("type", "string", "description", "资源名称关键词"));
        return props;
    }

    @Override
    public List<String> requiredParams() {
        return List.of();
    }

    @Override
    public String execute(AiToolContext ctx, Map<String, Object> args) throws Exception {
        Integer type = ToolUtils.intOrNull(args.get("type"));
        String keyword = ToolUtils.strOrNull(args.get("keyword"));
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
}

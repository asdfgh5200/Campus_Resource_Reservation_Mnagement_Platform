package com.campus.reservation.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具注册表：集中管理所有 {@link AiTool}，并按当前用户角色提供两层权限保护——
 * 1) {@link #schemasForRole(int)} 只把当前角色可用的工具暴露给模型（普通用户看不到管理员级工具）；
 * 2) {@link #dispatch} 执行前依据服务端角色二次校验，越权直接拒绝，杜绝模型幻觉调用管理员工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolRegistry {

    private final java.util.List<AiTool> tools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 构建面向指定角色的 tools schema 数组：角色不足的工具不会被暴露给模型 */
    public ArrayNode schemasForRole(int role) {
        int effective = role == AiTool.ROLE_ADMIN ? AiTool.ROLE_ADMIN : AiTool.ROLE_USER;
        ArrayNode arr = new ArrayNode(JsonNodeFactory.instance);
        for (AiTool t : tools) {
            if (t.requiredRole() > effective) {
                continue;
            }
            arr.add(buildSchema(t));
        }
        return arr;
    }

    /** 分派执行：未知工具与越权工具均返回错误观测，不执行真实逻辑 */
    public String dispatch(String name, Map<String, Object> args, AiToolContext ctx) {
        AiTool tool = tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
        if (tool == null) {
            return "{\"error\":\"未知工具 " + escape(name) + "\"}";
        }
        int effective = ctx.isAdmin() ? AiTool.ROLE_ADMIN : AiTool.ROLE_USER;
        if (tool.requiredRole() > effective) {
            log.warn("用户 {} (role={}) 试图调用越权工具 {}", ctx.getUserId(), ctx.getRole(), name);
            return "{\"error\":\"无权限执行该操作\"}";
        }
        try {
            return tool.execute(ctx, args);
        } catch (Exception e) {
            log.warn("工具 {} 执行异常", name, e);
            return "{\"error\":\"" + escape(e.getMessage()) + "\"}";
        }
    }

    private ObjectNode buildSchema(AiTool t) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "function");
        ObjectNode fn = node.putObject("function");
        fn.put("name", t.name());
        fn.put("description", t.description());
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        ObjectNode p = params.putObject("properties");
        t.parameterSchema().forEach((k, v) -> {
            try {
                p.set(k, objectMapper.valueToTree(v));
            } catch (Exception ignore) {
                // 忽略无法序列化的单个参数
            }
        });
        if (!t.requiredParams().isEmpty()) {
            ArrayNode req = params.putArray("required");
            t.requiredParams().forEach(req::add);
        }
        return node;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

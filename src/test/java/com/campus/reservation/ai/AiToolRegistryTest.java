package com.campus.reservation.ai;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiToolRegistry 权限门禁测试：
 * 普通用户会话既看不到管理员级工具的 schema，也无法通过 dispatch 执行管理员级工具。
 */
class AiToolRegistryTest {

    /** 普通用户可见的只读工具 */
    private static class UserTool implements AiTool {
        @Override
        public String name() {
            return "user_tool";
        }

        @Override
        public String description() {
            return "普通用户工具";
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
        public String execute(AiToolContext ctx, Map<String, Object> args) {
            return "{\"ok\":true}";
        }
    }

    /** 仅管理员可用的工具 */
    private static class AdminTool implements AiTool {
        @Override
        public String name() {
            return "admin_tool";
        }

        @Override
        public String description() {
            return "管理员工具";
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
            return AiTool.ROLE_ADMIN;
        }

        @Override
        public String execute(AiToolContext ctx, Map<String, Object> args) {
            return "{\"admin\":true}";
        }
    }

    private AiToolRegistry registry() {
        return new AiToolRegistry(List.of(new UserTool(), new AdminTool()));
    }

    @Test
    @DisplayName("schemasForRole：普通用户请求不暴露管理员级工具，管理员可见全部")
    void schemasForRole_filtersByRole() {
        ArrayNode userSchemas = registry().schemasForRole(AiTool.ROLE_USER);
        assertThat(toolNames(userSchemas)).contains("user_tool").doesNotContain("admin_tool");

        ArrayNode adminSchemas = registry().schemasForRole(AiTool.ROLE_ADMIN);
        assertThat(toolNames(adminSchemas)).contains("user_tool", "admin_tool");
    }

    @Test
    @DisplayName("dispatch：普通用户执行管理员级工具被拒绝，不触发真实逻辑")
    void dispatch_blocksPrivilegeEscalation() {
        AiToolContext userCtx = new AiToolContext(10L, AiTool.ROLE_USER, new java.util.ArrayList<>());
        String reply = registry().dispatch("admin_tool", Map.of(), userCtx);
        assertThat(reply).contains("无权限");
    }

    @Test
    @DisplayName("dispatch：管理员可正常执行管理员级工具")
    void dispatch_adminAllowed() {
        AiToolContext adminCtx = new AiToolContext(1L, AiTool.ROLE_ADMIN, new java.util.ArrayList<>());
        String reply = registry().dispatch("admin_tool", Map.of(), adminCtx);
        assertThat(reply).contains("\"admin\":true");
    }

    @Test
    @DisplayName("dispatch：未知工具返回错误")
    void dispatch_unknownTool() {
        AiToolContext ctx = new AiToolContext(10L, AiTool.ROLE_USER, new java.util.ArrayList<>());
        assertThat(registry().dispatch("no_such_tool", Map.of(), ctx)).contains("未知工具");
    }

    private static List<String> toolNames(ArrayNode arr) {
        List<String> names = new java.util.ArrayList<>();
        arr.forEach(n -> names.add(n.path("function").path("name").asText()));
        return names;
    }
}

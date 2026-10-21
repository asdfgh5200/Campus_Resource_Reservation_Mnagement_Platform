package com.campus.reservation.ai;

import java.util.List;
import java.util.Map;

/**
 * 智约机器人可调用的工具（OpenAI function calling 的一个 function）。
 * 每个工具自描述名称、说明、参数 schema 与所需最低角色，供 {@link AiToolRegistry} 按当前用户角色
 * 过滤暴露给模型，并在执行时强制校验权限——普通用户绝不能触发管理员级工具。
 */
public interface AiTool {

    /** 角色：普通用户 */
    int ROLE_USER = 0;
    /** 角色：管理员 */
    int ROLE_ADMIN = 1;

    /** function 名称，需与模型调用时的 tool name 对应 */
    String name();

    /** 供模型理解的函数说明 */
    String description();

    /** 参数 JSON schema 的 properties 部分：{ 参数名 -> {type, description...} } */
    Map<String, Object> parameterSchema();

    /** 必填参数名列表 */
    List<String> requiredParams();

    /** 执行该工具所需的最低角色：0 普通用户、1 管理员 */
    default int requiredRole() {
        return ROLE_USER;
    }

    /** 执行工具，返回回填给模型的观测结果（通常为 JSON 字符串） */
    String execute(AiToolContext ctx, Map<String, Object> args) throws Exception;
}

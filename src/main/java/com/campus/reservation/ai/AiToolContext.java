package com.campus.reservation.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单次工具调用的上下文：发起会话的用户、其角色，以及收集写操作草案的列表。
 */
@Data
@AllArgsConstructor
public class AiToolContext {
    /** 当前登录用户 ID */
    private Long userId;
    /** 当前登录用户角色：0 普通用户、1 管理员 */
    private int role;
    /** 写操作工具产出的草案，最终回传前端渲染确认卡片 */
    private List<Map<String, Object>> drafts;

    public boolean isAdmin() {
        return role == 1;
    }
}

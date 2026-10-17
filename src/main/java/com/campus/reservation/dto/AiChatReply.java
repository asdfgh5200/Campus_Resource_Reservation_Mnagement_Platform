package com.campus.reservation.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** /api/ai/chat 的返回：文本回复 + 待用户确认的草案动作 */
@Data
public class AiChatReply {
    /** 助手文本回复 */
    private String content;
    /**
     * 待用户确认的草案：
     * 每项 { kind: 'create_reservation'|'cancel_reservation', payload: {...} }
     * 前端渲染为卡片，用户点击"确认"后调用对应真实 API。
     */
    private List<Map<String, Object>> pendingActions;

    /** 回写给前端用于继续对话的助手消息（含在历史里，避免消息重复） */
    private Message assistantEcho;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}

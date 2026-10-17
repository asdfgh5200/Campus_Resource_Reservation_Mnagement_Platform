package com.campus.reservation.dto;

import lombok.Data;

import java.util.List;

/** 前端提交给 /api/ai/chat 的会话请求，只带用户 & 助手的可见历史 */
@Data
public class AiChatRequest {
    /** OpenAI 兼容的 message 列表：{ role: user|assistant|system, content: string } */
    private List<Message> messages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}

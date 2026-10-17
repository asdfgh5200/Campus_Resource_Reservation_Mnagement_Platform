package com.campus.reservation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** DeepSeek / OpenAI 兼容 API 的调用配置 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    /** 是否启用 AI 机器人；未配 api-key 时接口返回 503 */
    private boolean enabled = true;
    /** API base url，末尾无斜杠；例如 https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";
    /** 模型名，例如 deepseek-chat */
    private String model = "deepseek-chat";
    /** API Key */
    private String apiKey = "";
    /** 单次会话最多工具调用轮次，防止死循环 */
    private int maxToolLoops = 6;

    public boolean ready() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}

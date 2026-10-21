package com.campus.reservation.web;

import com.campus.reservation.controller.AiChatController;
import com.campus.reservation.dto.AiChatReply;
import com.campus.reservation.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 /api/ai/chat 能正常绑定 application/json;charset=UTF-8 请求体（防 415 回归）。
 */
class AiChatRequestBindingTest {

    @Test
    void bindsJsonBodyWithUtf8Charset() throws Exception {
        AiChatService service = mock(AiChatService.class);
        AiChatReply reply = new AiChatReply();
        reply.setContent("ok");
        when(service.chat(ArgumentMatchers.any())).thenReturn(reply);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiChatController(service)).build();
        mvc.perform(post("/api/ai/chat")
                        .contentType(new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8))
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("ok"));
    }
}

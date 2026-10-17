package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.dto.AiChatRequest;
import com.campus.reservation.dto.AiChatReply;
import com.campus.reservation.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 智约机器人：前端聊天悬浮窗调用的会话接口 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public Result<AiChatReply> chat(@RequestBody AiChatRequest req) {
        return Result.ok(aiChatService.chat(req));
    }
}

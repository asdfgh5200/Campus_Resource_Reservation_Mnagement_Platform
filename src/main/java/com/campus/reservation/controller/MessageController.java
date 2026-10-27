package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.UserMessage;
import com.campus.reservation.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内消息：用户留言 / 我的收件箱；管理员查看留言列表 / 回复。
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 用户向管理员留言 */
    @PostMapping("/ask")
    public Result<UserMessage> ask(@RequestBody Map<String, String> body) {
        return Result.ok(messageService.ask(body.get("content")));
    }

    /** 我的消息：我的留言 + 收到的回复/通知（用户端按「回复」「通知」分类展示） */
    @GetMapping("/mine")
    public Result<List<UserMessage>> mine() {
        return Result.ok(messageService.myMessages());
    }

    /** 我的未读数 */
    @GetMapping("/mine/unread")
    public Result<Map<String, Long>> myUnread() {
        return Result.ok(Map.of("count", messageService.myUnreadCount()));
    }

    /** 标记单条消息为已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    /** 管理端：分页查看用户留言 */
    @GetMapping("/admin/page")
    public Result<Map<String, Object>> adminPage(@RequestParam(required = false) Boolean unreadOnly,
                                                 @RequestParam(defaultValue = "1") Integer current,
                                                 @RequestParam(defaultValue = "10") Integer size) {
        UserContext.requireAdmin();
        return Result.ok(messageService.adminPage(unreadOnly, current, size));
    }

    /** 管理端：待处理留言数 */
    @GetMapping("/admin/unread")
    public Result<Map<String, Long>> adminUnread() {
        UserContext.requireAdmin();
        return Result.ok(Map.of("count", messageService.adminUnreadCount()));
    }

    /** 管理端：回复指定留言 */
    @PostMapping("/admin/reply/{askId}")
    public Result<UserMessage> reply(@PathVariable Long askId, @RequestBody Map<String, String> body) {
        UserContext.requireAdmin();
        return Result.ok(messageService.reply(askId, body.get("content")));
    }

    /** 管理端：广播通知（scope=all 全体用户 / active 正在使用中的用户） */
    @PostMapping("/admin/broadcast")
    public Result<Map<String, Object>> broadcast(@RequestBody Map<String, String> body) {
        int sent = messageService.broadcast(body.get("content"), body.get("scope"));
        return Result.ok(Map.of("sent", sent));
    }
}

package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.UserMessage;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.UserMessageMapper;
import com.campus.reservation.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内消息：用户给管理员留言、管理员回复；系统（资源变更等）向用户推送通知。
 * direction=1 用户→管理员；direction=2 管理员/系统→用户（refId 指向原留言，系统通知为 null）。
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final UserMessageMapper messageMapper;
    private final SysUserMapper userMapper;

    /** 用户给管理员留言 */
    @Transactional
    public UserMessage ask(String content) {
        if (!StringUtils.hasText(content)) {
            throw BizException.badRequest("留言内容不能为空");
        }
        if (content.length() > 1000) {
            throw BizException.badRequest("留言内容不能超过 1000 字");
        }
        UserMessage msg = new UserMessage();
        msg.setUserId(UserContext.requireUserId());
        msg.setDirection(UserMessage.DIRECTION_USER_TO_ADMIN);
        msg.setContent(content.trim());
        msg.setIsRead(0);
        messageMapper.insert(msg);
        return msg;
    }

    /** 我收到的消息（回复/通知），仅普通用户查看自己 */
    public List<UserMessage> myMessages() {
        Long userId = UserContext.requireUserId();
        return messageMapper.selectList(Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getDirection, UserMessage.DIRECTION_ADMIN_TO_USER)
                .orderByDesc(UserMessage::getCreatedAt));
    }

    /** 我收到的未读消息数 */
    public long myUnreadCount() {
        Long userId = UserContext.requireUserId();
        Long cnt = messageMapper.selectCount(Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getDirection, UserMessage.DIRECTION_ADMIN_TO_USER)
                .eq(UserMessage::getIsRead, 0));
        return cnt == null ? 0 : cnt;
    }

    /** 将自己的某条收到的消息标记已读 */
    @Transactional
    public void markRead(Long id) {
        UserMessage msg = messageMapper.selectById(id);
        if (msg == null || !msg.getUserId().equals(UserContext.requireUserId())) {
            throw BizException.forbidden("无权操作该消息");
        }
        msg.setIsRead(1);
        messageMapper.updateById(msg);
    }

    /** 管理端：留言列表（direction=1），附最近回复内容与是否已回复，按时间倒序 */
    public Map<String, Object> adminPage(Boolean unreadOnly, Integer current, Integer size) {
        UserContext.requireAdmin();
        int cur = current == null || current < 1 ? 1 : current;
        int sz = size == null || size < 1 ? 10 : size;
        Page<UserMessage> page = messageMapper.selectPage(new Page<>(cur, sz),
                Wrappers.<UserMessage>lambdaQuery()
                        .eq(UserMessage::getDirection, UserMessage.DIRECTION_USER_TO_ADMIN)
                        .eq(unreadOnly != null && unreadOnly, UserMessage::getIsRead, 0)
                        .orderByDesc(UserMessage::getCreatedAt));
        List<Long> userIds = page.getRecords().stream().map(UserMessage::getUserId).distinct().toList();
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<Long> askIds = page.getRecords().stream().map(UserMessage::getId).toList();
        Map<Long, UserMessage> replyByAskId = askIds.isEmpty() ? Map.of()
                : messageMapper.selectList(Wrappers.<UserMessage>lambdaQuery()
                        .eq(UserMessage::getDirection, UserMessage.DIRECTION_ADMIN_TO_USER)
                        .in(UserMessage::getRefId, askIds)).stream()
                        .collect(Collectors.toMap(UserMessage::getRefId, r -> r, (a, b) -> a));
        List<Map<String, Object>> records = page.getRecords().stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            SysUser u = users.get(m.getUserId());
            row.put("id", m.getId());
            row.put("userId", m.getUserId());
            row.put("username", u == null ? "已注销用户" : u.getUsername());
            row.put("nickname", u == null ? null : u.getNickname());
            row.put("content", m.getContent());
            row.put("isRead", m.getIsRead());
            row.put("createdAt", m.getCreatedAt());
            UserMessage reply = replyByAskId.get(m.getId());
            row.put("reply", reply == null ? null : reply.getContent());
            row.put("replied", reply != null);
            return row;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        return result;
    }

    /** 管理端：待处理（未读）留言数 */
    public long adminUnreadCount() {
        UserContext.requireAdmin();
        Long cnt = messageMapper.selectCount(Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getDirection, UserMessage.DIRECTION_USER_TO_ADMIN)
                .eq(UserMessage::getIsRead, 0));
        return cnt == null ? 0 : cnt;
    }

    /** 管理端：回复留言（同时把原留言标记为已处理） */
    @Transactional
    public UserMessage reply(Long askId, String content) {
        UserContext.requireAdmin();
        if (!StringUtils.hasText(content)) {
            throw BizException.badRequest("回复内容不能为空");
        }
        UserMessage ask = messageMapper.selectById(askId);
        if (ask == null || ask.getDirection() != UserMessage.DIRECTION_USER_TO_ADMIN) {
            throw BizException.notFound("留言不存在");
        }
        UserMessage reply = new UserMessage();
        reply.setUserId(ask.getUserId());
        reply.setDirection(UserMessage.DIRECTION_ADMIN_TO_USER);
        reply.setContent(content.trim());
        reply.setRefId(askId);
        reply.setIsRead(0);
        messageMapper.insert(reply);

        ask.setIsRead(1);
        messageMapper.updateById(ask);
        return reply;
    }

    /**
     * 系统通知（内部调用，不做登录校验）：向指定用户推送 direction=2 的消息。
     * 用于资源变更提醒等场景。
     */
    @Transactional
    public void notifyUser(Long userId, String content) {
        UserMessage msg = new UserMessage();
        msg.setUserId(userId);
        msg.setDirection(UserMessage.DIRECTION_ADMIN_TO_USER);
        msg.setContent(content);
        msg.setIsRead(0);
        messageMapper.insert(msg);
    }
}

package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.UserMessage;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.UserMessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MessageService 单元测试：只关心"落库字段"与"权限校验"，Mapper 全部 mock。
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private UserMessageMapper messageMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private MessageService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("ask：普通用户向管理员留言 → direction=1、isRead=0")
    void ask_success_insertsUserToAdmin() {
        UserContext.set(10L, 0);
        UserMessage saved = service.ask("请问实验室可以预约到几点？");
        ArgumentCaptor<UserMessage> cap = ArgumentCaptor.forClass(UserMessage.class);
        verify(messageMapper).insert(cap.capture());
        UserMessage v = cap.getValue();
        assertThat(v.getUserId()).isEqualTo(10L);
        assertThat(v.getDirection()).isEqualTo(UserMessage.DIRECTION_USER_TO_ADMIN);
        assertThat(v.getIsRead()).isZero();
        assertThat(v.getContent()).isEqualTo("请问实验室可以预约到几点？");
        assertThat(saved).isNotNull();
    }

    @Test
    @DisplayName("ask：空内容 → 400")
    void ask_blank_badRequest() {
        UserContext.set(10L, 0);
        assertThatThrownBy(() -> service.ask("  "))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
        verify(messageMapper, never()).insert(any(UserMessage.class));
    }

    @Test
    @DisplayName("reply：管理员回复留言 → 落库 direction=2/refId=askId，并把原留言标记已读")
    void reply_success_marksAskRead() {
        UserContext.set(1L, 1);
        UserMessage ask = new UserMessage();
        ask.setId(50L);
        ask.setUserId(10L);
        ask.setDirection(UserMessage.DIRECTION_USER_TO_ADMIN);
        ask.setIsRead(0);
        ask.setContent("资源被改了怎么办？");
        when(messageMapper.selectById(50L)).thenReturn(ask);

        service.reply(50L, "已为你重新分配座位，请查收通知。");

        ArgumentCaptor<UserMessage> cap = ArgumentCaptor.forClass(UserMessage.class);
        verify(messageMapper).insert(cap.capture());
        UserMessage reply = cap.getValue();
        assertThat(reply.getUserId()).isEqualTo(10L);
        assertThat(reply.getDirection()).isEqualTo(UserMessage.DIRECTION_ADMIN_TO_USER);
        assertThat(reply.getRefId()).isEqualTo(50L);
        assertThat(reply.getIsRead()).isZero();

        // 原留言被标记为已读
        assertThat(ask.getIsRead()).isEqualTo(1);
        verify(messageMapper).updateById(ask);
    }

    @Test
    @DisplayName("reply：非管理员调用 → 403")
    void reply_nonAdmin_forbidden() {
        UserContext.set(10L, 0);
        assertThatThrownBy(() -> service.reply(1L, "hello"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 403);
    }

    @Test
    @DisplayName("notifyUser：系统通知 refId=null，direction=2")
    void notifyUser_recordsSystemNotice() {
        service.notifyUser(99L, "管理员已修改资源");
        ArgumentCaptor<UserMessage> cap = ArgumentCaptor.forClass(UserMessage.class);
        verify(messageMapper).insert(cap.capture());
        UserMessage v = cap.getValue();
        assertThat(v.getUserId()).isEqualTo(99L);
        assertThat(v.getDirection()).isEqualTo(UserMessage.DIRECTION_ADMIN_TO_USER);
        assertThat(v.getRefId()).isNull();
    }
}

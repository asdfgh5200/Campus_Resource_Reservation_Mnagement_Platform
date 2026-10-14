package com.campus.reservation.service;

import com.campus.reservation.common.BizException;
import com.campus.reservation.common.JwtUtil;
import com.campus.reservation.dto.LoginDTO;
import com.campus.reservation.dto.RegisterDTO;
import com.campus.reservation.dto.UpdateUserDTO;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试：Mock SysUserMapper 与 JwtUtil，不启动 Spring、不连接 MySQL。
 * 覆盖登录 / 注册 / 个人信息（me / updateMe）的 happy path 与边界 / 异常路径。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    private UserService service;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        // UserService 仅有 (SysUserMapper, JwtUtil) 两个构造参数；encoder 为字段初始化
        service = new UserService(userMapper, jwtUtil);
    }

    // ---------------- 登录 ----------------

    @Test
    @DisplayName("登录：用户名为空 → 400")
    void login_blankUsername_badRequest() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("  ");
        dto.setPassword("secret123");
        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("登录：密码错误 → 401")
    void login_wrongPassword_unauthorized() {
        SysUser user = new SysUser();
        user.setUsername("alice");
        user.setPassword(encoder.encode("right-password"));
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("wrong-password");

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 401);
    }

    @Test
    @DisplayName("登录：账号被禁用 → 403")
    void login_disabledUser_forbidden() {
        SysUser user = new SysUser();
        user.setUsername("alice");
        user.setPassword(encoder.encode("secret123"));
        user.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 403);
    }

    @Test
    @DisplayName("登录：成功返回 token 与用户信息")
    void login_success_returnsToken() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(encoder.encode("secret123"));
        user.setStatus(1);
        user.setRole(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtil.generate(1L, 0)).thenReturn("mock-token");

        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");

        Map<String, Object> data = service.login(dto);
        assertThat(data.get("token")).isEqualTo("mock-token");
        assertThat(data.get("userInfo")).isSameAs(user);
    }

    // ---------------- 注册 ----------------

    @Test
    @DisplayName("注册：密码长度不足 → 400")
    void register_shortPassword_badRequest() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("123");
        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("注册：用户名格式不合法 → 400")
    void register_badUsernameFormat_badRequest() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("a b");
        dto.setPassword("secret123");
        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("注册：手机号格式不合法 → 400")
    void register_badPhone_badRequest() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");
        dto.setPhone("12345");
        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    @DisplayName("注册：用户名已存在 → 409")
    void register_duplicateUsername_conflict() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");
        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 409);
    }

    @Test
    @DisplayName("注册：成功插入并返回用户")
    void register_success_returnsUser() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("secret123");
        dto.setNickname("Alice");
        dto.setPhone("13800138000");

        SysUser user = service.register(dto);
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getRole()).isEqualTo(0);
        assertThat(user.getCreditScore()).isEqualTo(100);
        assertThat(user.getStatus()).isEqualTo(1);
        assertThat(encoder.matches("secret123", user.getPassword())).isTrue();
    }

    // ---------------- 个人信息 ----------------

    @Test
    @DisplayName("me：用户不存在 → 404")
    void me_notFound_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.me(99L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    @DisplayName("me：成功返回用户")
    void me_success_returnsUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectById(1L)).thenReturn(user);
        assertThat(service.me(1L)).isSameAs(user);
    }

    @Test
    @DisplayName("updateMe：仅更新提供的字段")
    void updateMe_keepsExistingFields() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("alice");
        user.setNickname("OldNick");
        user.setPhone("13800138000");

        SysUser updated = new SysUser();
        updated.setId(1L);
        updated.setUsername("alice");
        updated.setNickname("NewNick");
        updated.setPhone("13800138000");

        // updateMe 内部先 me() 查询一次、updateById 后再 me() 查询一次
        when(userMapper.selectById(1L)).thenReturn(user, updated);

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setNickname("NewNick");

        SysUser result = service.updateMe(1L, dto);
        assertThat(result.getNickname()).isEqualTo("NewNick");
        assertThat(result.getPhone()).isEqualTo("13800138000");
    }
}

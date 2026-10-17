package com.campus.reservation.service;

import com.campus.reservation.common.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtUtil 单元测试：签名生成 / 校验回环、篡改、过期、非法格式。
 * 纯逻辑无依赖，直接 new + ReflectionTestUtils 注入 @Value 字段。
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "unit-test-secret-key");
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 72L);
    }

    @Test
    @DisplayName("generate → validate 回环：还原 userId 与 role")
    void generate_validate_roundTrip() {
        String token = jwtUtil.generate(42L, 1);
        JwtUtil.TokenInfo info = jwtUtil.validate(token);
        assertThat(info).isNotNull();
        assertThat(info.userId()).isEqualTo(42L);
        assertThat(info.role()).isEqualTo(1);
    }

    @Test
    @DisplayName("签名被篡改 → 校验失败返回 null")
    void validate_tamperedSignature_returnsNull() {
        String token = jwtUtil.generate(1L, 0);
        String[] parts = token.split("\\.");
        // 翻转签名末位字符
        char last = parts[1].charAt(parts[1].length() - 1);
        String flipped = last == 'A' ? "B" : "A";
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1) + flipped;
        assertThat(jwtUtil.validate(tampered)).isNull();
    }

    @Test
    @DisplayName("payload 被篡改（冒充他人 uid）→ 校验失败返回 null")
    void validate_tamperedPayload_returnsNull() {
        String token = jwtUtil.generate(1L, 0);
        String signature = token.split("\\.")[1];
        // 用另一个用户的 payload 拼上原签名（越权伪造）
        String fakePayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("999.1." + (System.currentTimeMillis() + 3600_000)).getBytes());
        assertThat(jwtUtil.validate(fakePayload + "." + signature)).isNull();
    }

    @Test
    @DisplayName("token 已过期 → 校验失败返回 null")
    void validate_expired_returnsNull() {
        ReflectionTestUtils.setField(jwtUtil, "expireHours", -1L); // 生成即过期
        String token = jwtUtil.generate(1L, 0);
        assertThat(jwtUtil.validate(token)).isNull();
    }

    @Test
    @DisplayName("换用不同密钥无法校验通过（防密钥泄露后旧 token 复用）")
    void validate_differentSecret_returnsNull() {
        String token = jwtUtil.generate(1L, 0);
        ReflectionTestUtils.setField(jwtUtil, "secret", "another-secret-key");
        assertThat(jwtUtil.validate(token)).isNull();
    }

    @Test
    @DisplayName("null / 空串 / 格式非法 → 一律返回 null 不抛异常")
    void validate_malformed_returnsNull() {
        assertThat(jwtUtil.validate(null)).isNull();
        assertThat(jwtUtil.validate("")).isNull();
        assertThat(jwtUtil.validate("   ")).isNull();
        assertThat(jwtUtil.validate("not-a-token")).isNull();
        assertThat(jwtUtil.validate("a.b.c")).isNull();
        assertThat(jwtUtil.validate("!!!.!!!")).isNull();
    }
}

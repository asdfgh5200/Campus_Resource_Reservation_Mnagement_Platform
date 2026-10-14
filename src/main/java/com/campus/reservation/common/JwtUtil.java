package com.campus.reservation.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 轻量签名 Token（HMAC-SHA256），格式：base64url(uid.role.exp) + "." + 签名
 * 无第三方依赖，适合课程设计场景。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:72}")
    private long expireHours;

    public record TokenInfo(Long userId, Integer role) {
    }

    public String generate(Long userId, Integer role) {
        long exp = Instant.now().plus(expireHours, ChronoUnit.HOURS).toEpochMilli();
        String payload = userId + "." + role + "." + exp;
        String p = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(p));
    }

    public TokenInfo validate(String token) {
        try {
            if (token == null || token.isBlank()) {
                return null;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return null;
            }
            byte[] expected = hmac(parts[0]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] seg = payload.split("\\.");
            if (seg.length != 3) {
                return null;
            }
            if (Long.parseLong(seg[2]) < System.currentTimeMillis()) {
                return null;
            }
            return new TokenInfo(Long.parseLong(seg[0]), Integer.parseInt(seg[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}

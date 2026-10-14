package com.campus.reservation.common;

import lombok.AllArgsConstructor;
import lombok.Data;

public class UserContext {

    @Data
    @AllArgsConstructor
    public static class LoginUser {
        private Long userId;
        private Integer role;
    }

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(Long userId, Integer role) {
        HOLDER.set(new LoginUser(userId, role));
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long requireUserId() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return u.getUserId();
    }

    public static void requireAdmin() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        if (u.getRole() == null || u.getRole() != 1) {
            throw BizException.forbidden("无权限访问");
        }
    }
}

package com.campus.reservation.ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 工具参数解析与时间归一化的公共小工具 */
public final class ToolUtils {

    private ToolUtils() {
    }

    public static Integer intOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    public static Long longOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    public static String strOrNull(Object v) {
        return v == null ? null : v.toString();
    }

    /** 布尔参数：兼容 true/false 与 "true"/"false"/"1"/"0" 两种模型输出风格 */
    public static Boolean boolOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    /**
     * 将 LLM 可能输出的 ISO / 缺秒 / 带 T 的时间字符串归一化为项目统一的 yyyy-MM-dd HH:mm:ss。
     * 无法解析时返回 null。
     */
    public static String normalizeDateTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace('T', ' ');
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        int plus = s.indexOf('+');
        if (plus > 0) s = s.substring(0, plus);
        if (s.length() == 16) s = s + ":00";   // yyyy-MM-dd HH:mm → 补秒
        if (s.length() != 19) return null;
        try {
            LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}

package com.campus.reservation.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 教学周日历：把自然日换算成教学周，并判断某条课表在指定周是否真的上课。
 *
 * <p>教务课表里的课程并非每周都有（例如「8周」「13-15周(单)」），课表记录用 30 位位图
 * {@code weeks} 表示上课周次：第 n 个字符为 '1' 即第 n 周上课。位图为空的记录视为每周上课，
 * 兼容升级前导入的历史数据。
 *
 * <p>学期首周周一取自教务「校历」（{@code XQKSRQ}）：2026-2027-1 学期为 2026-09-07。
 */
public final class TermCalendar {

    /** 当前课表所属学期 */
    public static final String TERM = "2026-2027-1";

    /** 学期第 1 周的周一（教务校历第一教学周起始日） */
    public static final LocalDate TERM_START = LocalDate.of(2026, 9, 7);

    /** 位图最多支持的周数 */
    public static final int MAX_WEEKS = 30;

    private TermCalendar() {
    }

    /**
     * 自然日 → 教学周。早于学期首周返回 0 或负数，意味着「不在本学期教学周内」。
     */
    public static int weekOf(LocalDate date) {
        if (date == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(TERM_START, date);
        return (int) Math.floorDiv(days, 7L) + 1;
    }

    /** 该自然日是否为学期内的正常教学日（周末仍算教学周，只是通常没课） */
    public static boolean inTerm(LocalDate date) {
        return weekOf(date) >= 1;
    }

    /**
     * 课表记录在指定周是否上课。位图为空（历史数据）时一律视为上课。
     */
    public static boolean occupies(String weeks, int week) {
        if (weeks == null || weeks.isBlank()) {
            return true;
        }
        int idx = week - 1;
        if (idx < 0 || idx >= weeks.length()) {
            return false;
        }
        return weeks.charAt(idx) == '1';
    }

    /** 同 {@link #occupies(String, int)}，但按自然日换算周次 */
    public static boolean occupiesOn(String weeks, LocalDate date) {
        return occupies(weeks, weekOf(date));
    }

    /** 某条课记录一共排了多少个教学周（位图为空返回 0，表示未知） */
    public static int weekCount(String weeks) {
        if (weeks == null || weeks.isBlank()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < weeks.length(); i++) {
            if (weeks.charAt(i) == '1') {
                n++;
            }
        }
        return n;
    }

    /**
     * 位图 → 人话，格式与教务「周次列」一致：
     * 连续周写成区间（{@code 1-16 周}），隔周上课合并为单双周（{@code 9-15 周(单)}），
     * 散周逐个列出（如 {@code 8 周、12 周}）。位图为空返回「每周」。
     */
    public static String weekLabel(String weeks) {
        if (weeks == null || weeks.isBlank()) {
            return "每周";
        }
        List<Integer> list = weekNumbers(weeks);
        if (list.isEmpty()) {
            return "本学期无课";
        }
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            int start = list.get(i);
            // 连续周：step = 1
            int continuous = i;
            while (continuous + 1 < list.size() && list.get(continuous + 1) == list.get(continuous) + 1) {
                continuous++;
            }
            // 单双周：step = 2
            int oddEven = i;
            while (oddEven + 1 < list.size() && list.get(oddEven + 1) == list.get(oddEven) + 2) {
                oddEven++;
            }
            // 至少 3 个且不短于连续段时，按“单/双周”压缩，否则按连续区间输出
            if (oddEven - i >= 2 && oddEven > continuous) {
                parts.add(rangeText(start, list.get(oddEven)) + (start % 2 == 1 ? "(单)" : "(双)"));
                i = oddEven + 1;
            } else if (continuous > i) {
                parts.add(rangeText(start, list.get(continuous)));
                i = continuous + 1;
            } else {
                parts.add(start + " 周");
                i++;
            }
        }
        return String.join("、", parts);
    }

    /** 区间文案，如 {@code 9-15 周}；首尾相同时退化为单周 */
    private static String rangeText(int start, int end) {
        return start == end ? start + " 周" : start + "-" + end + " 周";
    }

    /** 位图中所有为 '1' 的周次（升序） */
    public static List<Integer> weekNumbers(String weeks) {
        List<Integer> list = new ArrayList<>();
        if (weeks == null) {
            return list;
        }
        for (int i = 0; i < weeks.length(); i++) {
            if (weeks.charAt(i) == '1') {
                list.add(i + 1);
            }
        }
        return list;
    }

    /** 周一为首日的星期序号，与 course_schedule.day_of_week 对齐 */
    public static int dayOfWeek(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d.getValue();
    }
}

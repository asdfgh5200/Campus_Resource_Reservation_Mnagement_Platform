package com.campus.reservation.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 教学周换算与周次位图判定：课表「哪一周上课」全靠这里，错了会把空教室判成占用。
 */
class TermCalendarTest {

    @Test
    @DisplayName("学期首周周一为第 1 周，按 7 天一周边界推进")
    void weekOfCountsFromTermStart() {
        LocalDate termStart = TermCalendar.TERM_START;
        assertThat(TermCalendar.weekOf(termStart)).isEqualTo(1);
        // 首周周日的最后一天仍是第 1 周
        assertThat(TermCalendar.weekOf(termStart.plusDays(6))).isEqualTo(1);
        // 第 2 周周一
        assertThat(TermCalendar.weekOf(termStart.plusDays(7))).isEqualTo(2);
        // 第 8 周周三（教务「当前第 8 周」的样本日期）
        assertThat(TermCalendar.weekOf(termStart.plusDays(7 * 7 + 2))).isEqualTo(8);
        assertThat(TermCalendar.dayOfWeek(termStart)).isEqualTo(1);
    }

    @Test
    @DisplayName("学期开始之前不是教学周，返回 0 或负数")
    void weekOfBeforeTermIsNotTeachingWeek() {
        assertThat(TermCalendar.weekOf(TermCalendar.TERM_START.minusDays(1))).isZero();
        assertThat(TermCalendar.weekOf(TermCalendar.TERM_START.minusDays(30))).isNegative();
        assertThat(TermCalendar.inTerm(TermCalendar.TERM_START.minusDays(1))).isFalse();
        assertThat(TermCalendar.weekOf(null)).isZero();
    }

    @Test
    @DisplayName("位图第 n 位对应第 n 周；位图为空视为每周上课（历史数据兼容）")
    void occupiesReadsBitmapFromLeft() {
        // 仅第 8 周上课：教务 SKZC 原样，第 8 个字符为 '1'
        String onlyWeek8 = "000000010000000000000000000000";
        assertThat(TermCalendar.occupies(onlyWeek8, 8)).isTrue();
        assertThat(TermCalendar.occupies(onlyWeek8, 7)).isFalse();
        assertThat(TermCalendar.occupies(onlyWeek8, 9)).isFalse();

        // 第 1~16 周连续上课
        String firstHalf = "1".repeat(16) + "0".repeat(14);
        assertThat(TermCalendar.occupies(firstHalf, 1)).isTrue();
        assertThat(TermCalendar.occupies(firstHalf, 16)).isTrue();
        assertThat(TermCalendar.occupies(firstHalf, 17)).isFalse();

        // 超过位图长度的周次不算上课，避免数组越界
        assertThat(TermCalendar.occupies("1", 30)).isFalse();
        // 历史数据没有位图 → 一律视为占用，宁可多拦也不放行
        assertThat(TermCalendar.occupies(null, 8)).isTrue();
        assertThat(TermCalendar.occupies("", 8)).isTrue();
        // 不在教学周（week <= 0）时，即使位图首位为 1 也不上课
        assertThat(TermCalendar.occupies("1".repeat(30), 0)).isFalse();
    }

    @Test
    @DisplayName("occupyOn 按自然日换算周次")
    void occupiesOnUsesDateWeek() {
        String onlyWeek8 = "000000010000000000000000000000";
        LocalDate week8Wed = TermCalendar.TERM_START.plusDays(7 * 7 + 2);
        assertThat(TermCalendar.occupiesOn(onlyWeek8, week8Wed)).isTrue();
        assertThat(TermCalendar.occupiesOn(onlyWeek8, week8Wed.plusDays(7))).isFalse();
    }

    @Test
    @DisplayName("周次数量与中文说明：连续区间合并，单周逐个列出")
    void weekCountAndLabel() {
        assertThat(TermCalendar.weekCount("000000010000000000000000000000")).isEqualTo(1);
        assertThat(TermCalendar.weekCount("1".repeat(16) + "0".repeat(14))).isEqualTo(16);
        assertThat(TermCalendar.weekCount(null)).isZero();

        assertThat(TermCalendar.weekLabel("000000010000000000000000000000")).isEqualTo("8 周");
        assertThat(TermCalendar.weekLabel("1".repeat(16) + "0".repeat(14))).isEqualTo("1-16 周");
        // 散周逐个列出，隔周连续段合并为单双周（与教务周次列文案一致）
        assertThat(TermCalendar.weekLabel("000000010010101000000000000000")).isEqualTo("8 周、11-15 周(单)");
        // 双周：第 2、4、6 … 16 周
        String even = "01".repeat(8) + "0".repeat(14);
        assertThat(TermCalendar.weekLabel(even)).isEqualTo("2-16 周(双)");
        // 位图全 0 表示这学期该时段没课
        assertThat(TermCalendar.weekLabel("0".repeat(30))).isEqualTo("本学期无课");
        // 历史数据没有位图 → 每周
        assertThat(TermCalendar.weekLabel(null)).isEqualTo("每周");
        assertThat(TermCalendar.weekLabel("  ")).isEqualTo("每周");
    }

    @Test
    @DisplayName("weekNumbers 按位图返回真实周次，用于单双周展开")
    void weekNumbersList() {
        assertThat(TermCalendar.weekNumbers("000000010010101000000000000000"))
                .containsExactly(8, 11, 13, 15);
        assertThat(TermCalendar.weekNumbers(null)).isEmpty();
        assertThat(TermCalendar.weekNumbers("000")).isEmpty();
    }
}

package com.campus.reservation.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class CourseImportDTO {
    private Long roomId;
    private String courseName;
    /** 星期：1~7 */
    private Integer dayOfWeek;
    /** 格式：HH:mm:ss */
    private LocalTime startTime;
    private LocalTime endTime;
    private String teacher;
    private String term;
    /**
     * 上课周次位图（可选）：第 n 个字符为 '1' 表示第 n 周上课。
     * 留空表示每周都上，兼容旧数据与手工录入。
     */
    private String weeks;
}

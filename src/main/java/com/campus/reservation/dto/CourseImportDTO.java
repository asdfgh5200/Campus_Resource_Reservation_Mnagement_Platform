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
}

package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("course_schedule")
public class CourseSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 教室（resource.id） */
    private Long roomId;

    private String courseName;

    /** 星期：1~7（周一~周日） */
    private Integer dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private String teacher;

    private String term;

    /**
     * 上课周次位图：第 n 个字符为 '1' 表示第 n 周上课（最多 30 周）。
     * 为空表示每周都上（历史数据）。教务系统 SKZC 字段原样保存。
     */
    private String weeks;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

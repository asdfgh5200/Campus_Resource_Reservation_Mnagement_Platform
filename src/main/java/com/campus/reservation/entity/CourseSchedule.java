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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

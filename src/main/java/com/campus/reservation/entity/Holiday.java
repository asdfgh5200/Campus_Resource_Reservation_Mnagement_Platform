package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("holiday")
public class Holiday {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

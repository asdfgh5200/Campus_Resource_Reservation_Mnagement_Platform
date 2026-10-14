package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("checkin_code")
public class CheckinCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private String code;

    private LocalDate validDate;

    private Integer used;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

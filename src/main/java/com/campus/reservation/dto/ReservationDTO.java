package com.campus.reservation.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationDTO {
    private Long resourceId;
    /** 格式：yyyy-MM-dd HH:mm:ss */
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 使用人数：体育馆多人预约时填写，其他资源忽略（固定 1） */
    private Integer headcount;
    private String remark;
}

package com.campus.reservation.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 管理员创建按资源类型的定时闭馆请求 */
@Data
public class ClosureDTO {
    /** 资源类型：2 实验室、3 图书馆、4 体育馆 */
    private Integer resourceType;
    /** 闭馆日期 */
    private LocalDate closureDate;
    /** 闭馆开始时刻 */
    private LocalTime startTime;
    /** 闭馆结束时刻 */
    private LocalTime endTime;
    /** 闭馆原因 */
    private String reason;
}

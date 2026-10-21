package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 按资源类型的定时闭馆：管理员可对实验室(2)/图书馆(3)/体育馆(4)在某个具体日期的具体时段设置闭馆。
 * 与 {@link SystemConfig#KEY_CAMPUS_CLOSED} 的总体一键闭馆开关并存、互不影响。
 */
@Data
@TableName("campus_closure")
public class CampusClosure {

    /** 是否生效：1 生效中 */
    public static final int ENABLED = 1;
    /** 是否生效：0 已撤销 */
    public static final int DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 闭馆资源类型：2 实验室、3 图书馆、4 体育馆 */
    private Integer resourceType;

    /** 闭馆日期 */
    private LocalDate closureDate;

    /** 闭馆开始时刻 */
    private LocalTime startTime;

    /** 闭馆结束时刻 */
    private LocalTime endTime;

    private String reason;

    /** 是否生效：1 生效、0 已撤销 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

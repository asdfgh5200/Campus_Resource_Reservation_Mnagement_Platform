package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reservation")
public class Reservation {

    /** 状态：1 待打卡、2 使用中、3 已完成、4 已取消、5 爽约、6 违规 */
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_IN_USE = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_NOSHOW = 5;
    public static final int STATUS_VIOLATED = 6;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    /** 半开区间 [startTime, endTime) */
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 打卡方式：2 验证码（当前仅支持验证码打卡） */
    private Integer checkinType;

    /** 是否已打卡：0 否、1 是 */
    private Integer checkinFlag;

    private LocalDateTime checkinTime;

    /**
     * 暂离截止时间（非空表示暂离中），超时未返回将自动释放并记违规。
     * updateStrategy = IGNORED：允许把该字段显式更新为 NULL（结束暂离/释放时必须清空）。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime awayUntil;

    /** 使用人数（体育馆支持多人预约，同一时段人数之和不超过场馆容量；其他资源固定 1） */
    private Integer headcount;

    private String remark;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 关联资源（非数据库字段） */
    @TableField(exist = false)
    private Resource resource;

    /** 关联用户（非数据库字段，管理端展示用） */
    @TableField(exist = false)
    private SysUser user;

    /** 关联资源当日验证码（非数据库字段，管理端展示用） */
    @TableField(exist = false)
    private String checkinCode;
}

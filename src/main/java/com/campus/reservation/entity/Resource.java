package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("resource")
public class Resource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 类型：1 教室、2 实验室、3 图书馆、4 体育馆 */
    private Integer resourceType;

    /** 校区：马房山校区 / 南湖校区 / 余家头校区 / 军山校区 */
    private String campus;

    private String building;

    private String floor;

    /** 教室类型：多媒体教室 / 普通教室 / 机房 / 制图教室 / 学院专用 / 实验室 等 */
    private String roomType;

    private String roomNo;

    private Integer capacity;

    /** 计量单位：人 / 台 / 片 等（体育馆各场馆不同） */
    private String unit;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalTime openTime;

    private LocalTime closeTime;

    /** 单次预约时长上限（分钟） */
    private Integer maxDuration;

    /** 可预约 */
    public static final int STATUS_AVAILABLE = 1;
    /** 使用中（管理员手动占用） */
    public static final int STATUS_IN_USE = 2;
    /** 维修中（报修工单已确认，正在维修） */
    public static final int STATUS_REPAIRING = 3;
    /** 报修中（用户已提交报修，等待管理员确认） */
    public static final int STATUS_REPORTED = 4;

    /** 状态：1 可预约、2 使用中、3 维修中、4 报修中 */
    private Integer status;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 今日是否已被预约（存在待打卡/使用中的预约），非数据库字段，列表展示用 */
    @TableField(exist = false)
    private Boolean reserved;

    /** 今日的预约是否属于当前登录用户，非数据库字段 */
    @TableField(exist = false)
    private Boolean reservedByMe;

    /** 被他人占用时的预约开始时间，非数据库字段 */
    @TableField(exist = false)
    private LocalDateTime reservedStart;

    /** 被他人占用时的预约结束时间，非数据库字段 */
    @TableField(exist = false)
    private LocalDateTime reservedEnd;

    /** 被他人占用时对方填写的备注，非数据库字段 */
    @TableField(exist = false)
    private String reservedRemark;

    /** 教室当前是否正在上课（教室不可预约，仅用于展示占用），非数据库字段 */
    @TableField(exist = false)
    private Boolean courseBusy;

    /** 教室当前正在上的课程名，非数据库字段 */
    @TableField(exist = false)
    private String courseNowName;

    /** 体育馆当前时段已约人数，非数据库字段（剩余 = capacity - occupiedNow） */
    @TableField(exist = false)
    private Integer occupiedNow;
}

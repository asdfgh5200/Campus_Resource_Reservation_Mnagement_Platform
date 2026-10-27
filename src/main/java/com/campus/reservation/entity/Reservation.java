package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
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

    /** 打卡方式：1 定位打卡（主流程）、2 验证码打卡（管理员现场补签）、3 管理员后台补签 */
    public static final int CHECKIN_TYPE_LOCATION = 1;
    public static final int CHECKIN_TYPE_CODE = 2;
    public static final int CHECKIN_TYPE_ADMIN = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    /** 半开区间 [startTime, endTime) */
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 打卡方式：1 定位、2 验证码 */
    private Integer checkinType;

    /** 是否已打卡：0 否、1 是 */
    private Integer checkinFlag;

    private LocalDateTime checkinTime;

    /** 打卡时的用户定位纬度（WGS-84，仅定位打卡有值，供管理端追溯） */
    private BigDecimal checkinLatitude;

    /** 打卡时的用户定位经度（WGS-84） */
    private BigDecimal checkinLongitude;

    /** 打卡时距资源打卡基准点的距离（米） */
    private Integer checkinDistance;

    /** 打卡时的浏览器定位精度（米） */
    private Integer checkinAccuracy;

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

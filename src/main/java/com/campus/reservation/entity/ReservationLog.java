package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 预约操作日志：记录用户对预约的每一次操作，供管理端审计 */
@Data
@TableName("reservation_log")
public class ReservationLog {

    public static final int ACTION_CREATE = 1;
    public static final int ACTION_CANCEL = 2;
    public static final int ACTION_CHECKIN = 3;
    public static final int ACTION_NOSHOW = 4;
    public static final int ACTION_COMPLETE = 5;
    /** 提前释放空间 */
    public static final int ACTION_RELEASE = 6;
    /** 暂离 / 结束暂离 */
    public static final int ACTION_AWAY = 7;
    /** 暂离超时未返回，系统自动释放 */
    public static final int ACTION_AWAY_TIMEOUT = 8;
    /** 用户删除自己的预约记录（仅限已结束的预约） */
    public static final int ACTION_DELETE = 9;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 冗余存用户名，保证用户被删除后日志仍可读 */
    private String username;

    private String nickname;

    private Long reservationId;

    private Long resourceId;

    private String resourceName;

    /** 操作类型：1 创建、2 取消、3 打卡、4 爽约、5 完成、6 提前释放、7 暂离、8 暂离超时、9 删除记录 */
    private Integer action;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

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
    // 7、8 为已下线的暂离功能（历史日志中仍可能出现，前端保留对应文案）
    public static final int ACTION_AWAY = 7;
    public static final int ACTION_AWAY_TIMEOUT = 8;
    /** 用户删除自己的预约记录（仅限已结束的预约） */
    public static final int ACTION_DELETE = 9;
    /** 管理员直接调整预约状态 */
    public static final int ACTION_ADMIN_UPDATE = 10;

    /** 操作人角色：0 用户本人、1 管理员、2 系统自动 */
    public static final int OPERATOR_USER = 0;
    public static final int OPERATOR_ADMIN = 1;
    public static final int OPERATOR_SYSTEM = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 冗余存用户名，保证用户被删除后日志仍可读 */
    private String username;

    private String nickname;

    private Long reservationId;

    private Long resourceId;

    private String resourceName;

    /** 操作类型：1 创建、2 取消、3 打卡、4 爽约、5 完成、6 提前释放、7 暂离（已下线）、8 暂离超时（已下线）、9 删除记录、10 管理员调整 */
    private Integer action;

    /** 操作人 ID（与预约归属用户区分：管理员调整时这里是管理员） */
    private Long operatorId;

    /** 操作人名称（冗余，管理员操作时为管理员姓名） */
    private String operatorName;

    /** 操作人角色：0 用户本人、1 管理员、2 系统自动 */
    private Integer operatorRole;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

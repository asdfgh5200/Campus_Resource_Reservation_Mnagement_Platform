package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String nickname;

    /** 角色：0 用户、1 管理员 */
    private Integer role;

    private String phone;

    private String email;

    /** 信用分（历史字段，界面不再展示） */
    private Integer creditScore;

    /** 累计取消预约次数，上限见 ReservationService.CANCEL_LIMIT */
    private Integer cancelCount;

    /** 累计预约次数：创建预约时自增，管理端可直接修正（不影响预约记录） */
    private Integer reservationTotal;

    /** 预约权限覆盖：0 按违规规则、1 强制正常、2 强制受限（管理员可改） */
    private Integer restrictOverride;

    /** 状态：1 正常、0 禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}

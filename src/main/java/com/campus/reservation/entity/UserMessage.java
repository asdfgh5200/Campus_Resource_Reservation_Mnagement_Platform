package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_message")
public class UserMessage {

    /** 方向：用户 → 管理员（留言/咨询） */
    public static final int DIRECTION_USER_TO_ADMIN = 1;
    /** 方向：管理员/系统 → 用户（回复/通知） */
    public static final int DIRECTION_ADMIN_TO_USER = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID：留言=发起人，回复/通知=接收人 */
    private Long userId;

    private Integer direction;

    private String content;

    /** 回复指向的原留言 ID */
    private Long refId;

    /** 关联资源 ID：维修进度等通知可据此跳转到对应地点 */
    private Long resourceId;

    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

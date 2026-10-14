package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_order")
public class RepairOrder {

    /** 状态：0 待处理、1 处理中、2 已完成、3 已驳回 */
    public static final int STATUS_TODO = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_REJECTED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private Long userId;

    private String problem;

    private String imageUrl;

    private Integer status;

    private Long handleUser;

    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 关联资源（非数据库字段） */
    @TableField(exist = false)
    private Resource resource;
}

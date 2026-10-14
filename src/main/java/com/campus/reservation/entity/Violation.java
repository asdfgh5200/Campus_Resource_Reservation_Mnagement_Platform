package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("violation")
public class Violation {

    /** 类型：1 爽约、2 超时使用、3 恶意取消、4 其他 */
    public static final int TYPE_NOSHOW = 1;
    public static final int TYPE_OVERTIME = 2;
    public static final int TYPE_BAD_CANCEL = 3;
    public static final int TYPE_OTHER = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long reservationId;

    private Integer type;

    private String description;

    private Integer deductScore;

    /** 状态：1 有效、0 已撤销 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

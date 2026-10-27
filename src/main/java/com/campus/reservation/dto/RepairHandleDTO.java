package com.campus.reservation.dto;

import lombok.Data;

@Data
public class RepairHandleDTO {
    /** 1 处理中、2 已完成、3 已驳回 */
    private Integer status;

    /** 驳回原因：status=3 时填写，会随通知发给报修人 */
    private String reason;
}

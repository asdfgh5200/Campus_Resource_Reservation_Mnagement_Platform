package com.campus.reservation.dto;

import lombok.Data;

@Data
public class RepairHandleDTO {
    /** 1 处理中、2 已完成、3 已驳回 */
    private Integer status;
}

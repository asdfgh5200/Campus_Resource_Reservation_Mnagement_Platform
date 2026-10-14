package com.campus.reservation.dto;

import lombok.Data;

@Data
public class ViolationDTO {
    private Long userId;
    private Long reservationId;
    /** 1 爽约、2 超时使用、3 恶意取消、4 其他 */
    private Integer type;
    private String description;
    private Integer deductScore;
}

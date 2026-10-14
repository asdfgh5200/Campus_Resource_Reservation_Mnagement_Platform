package com.campus.reservation.dto;

import lombok.Data;

@Data
public class CheckinVerifyDTO {
    private Long reservationId;
    private String code;
}

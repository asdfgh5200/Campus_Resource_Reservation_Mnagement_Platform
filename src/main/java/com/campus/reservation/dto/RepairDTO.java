package com.campus.reservation.dto;

import lombok.Data;

@Data
public class RepairDTO {
    private Long resourceId;
    private String problem;
    private String imageUrl;
}

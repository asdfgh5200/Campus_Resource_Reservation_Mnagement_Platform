package com.campus.reservation.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HolidayDTO {
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
}

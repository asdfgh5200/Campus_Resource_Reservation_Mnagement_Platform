package com.campus.reservation.dto;

import lombok.Data;

@Data
public class CheckinVerifyDTO {
    private Long reservationId;
    private String code;
    /** 浏览器定位返回的 WGS-84 纬度 */
    private Double latitude;
    /** 浏览器定位返回的 WGS-84 经度 */
    private Double longitude;
    /** 浏览器报告的定位精度（米） */
    private Double accuracy;
}

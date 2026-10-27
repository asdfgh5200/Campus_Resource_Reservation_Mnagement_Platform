package com.campus.reservation.dto;

import lombok.Data;

/**
 * 登录参数：学号 + 密码。学号复用 username 字段（登录名）。
 */
@Data
public class LoginDTO {
    /** 学号（登录名） */
    private String username;
    /** 密码 */
    private String password;
}

package com.campus.reservation.dto;

import lombok.Data;

/**
 * 注册参数：只收集姓名、学号、密码三项。
 * 学号复用 username 字段（登录名），姓名写入 nickname。
 */
@Data
public class RegisterDTO {
    /** 学号（登录名） */
    private String username;
    /** 密码 */
    private String password;
    /** 姓名 */
    private String nickname;
}

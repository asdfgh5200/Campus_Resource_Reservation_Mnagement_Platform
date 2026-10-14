package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.dto.LoginDTO;
import com.campus.reservation.dto.RegisterDTO;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @PostMapping("/register")
    public Result<SysUser> register(@RequestBody RegisterDTO dto) {
        return Result.ok(userService.register(dto));
    }
}

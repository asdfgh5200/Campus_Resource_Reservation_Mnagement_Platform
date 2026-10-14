package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.UpdateUserDTO;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<SysUser> me() {
        return Result.ok(userService.me(UserContext.requireUserId()));
    }

    @PutMapping("/me")
    public Result<SysUser> updateMe(@RequestBody UpdateUserDTO dto) {
        return Result.ok(userService.updateMe(UserContext.requireUserId(), dto));
    }
}

package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.JwtUtil;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.dto.LoginDTO;
import com.campus.reservation.dto.RegisterDTO;
import com.campus.reservation.dto.UpdateUserDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public Map<String, Object> login(LoginDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw BizException.badRequest("用户名和密码不能为空");
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("账号已被禁用");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generate(user.getId(), user.getRole()));
        data.put("userInfo", user);
        return data;
    }

    public SysUser register(RegisterDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw BizException.badRequest("用户名和密码不能为空");
        }
        String username = dto.getUsername().trim();
        if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw BizException.badRequest("用户名需为 3-20 位字母、数字或下划线");
        }
        if (dto.getPassword().length() < 6 || dto.getPassword().length() > 32) {
            throw BizException.badRequest("密码长度需为 6-32 位");
        }
        if (dto.getNickname() != null && dto.getNickname().trim().length() > 20) {
            throw BizException.badRequest("姓名/昵称不能超过 20 个字符");
        }
        if (dto.getPhone() != null && !dto.getPhone().isBlank()
                && !dto.getPhone().trim().matches("^1[3-9]\\d{9}$")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        Long cnt = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (cnt > 0) {
            throw BizException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank() ? username : dto.getNickname().trim());
        user.setPhone(dto.getPhone() == null || dto.getPhone().isBlank() ? null : dto.getPhone().trim());
        user.setRole(0);
        user.setCreditScore(100);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    public SysUser me(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    public SysUser updateMe(Long userId, UpdateUserDTO dto) {
        SysUser user = me(userId);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setNickname(dto.getNickname() != null ? dto.getNickname() : user.getNickname());
        update.setPhone(dto.getPhone() != null ? dto.getPhone() : user.getPhone());
        update.setEmail(dto.getEmail() != null ? dto.getEmail() : user.getEmail());
        userMapper.updateById(update);
        return me(userId);
    }
}

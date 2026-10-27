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
            throw BizException.badRequest("学号和密码不能为空");
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername().trim()));
        // 账号不存在单独提示，前端据此引导用户先注册
        if (user == null) {
            throw new BizException(404, "该学号尚未注册，请先注册");
        }
        if (!encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "学号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("账号已被禁用");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.generate(user.getId(), user.getRole()));
        data.put("userInfo", user);
        return data;
    }

    /** 注册：姓名、学号、密码三项，学号作为登录名 */
    public SysUser register(RegisterDTO dto) {
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        String nickname = dto.getNickname() == null ? "" : dto.getNickname().trim();
        String password = dto.getPassword() == null ? "" : dto.getPassword();
        if (username.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            throw BizException.badRequest("姓名、学号和密码不能为空");
        }
        if (!username.matches("^[0-9A-Za-z]{4,20}$")) {
            throw BizException.badRequest("学号需为 4-20 位数字或字母");
        }
        if (password.length() < 6 || password.length() > 32) {
            throw BizException.badRequest("密码长度需为 6-32 位");
        }
        if (nickname.length() > 20) {
            throw BizException.badRequest("姓名不能超过 20 个字符");
        }
        Long cnt = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (cnt > 0) {
            throw BizException.conflict("该学号已注册，请直接登录");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname);
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

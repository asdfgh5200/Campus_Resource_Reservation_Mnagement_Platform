package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.entity.ReservationLog;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.ReservationLogMapper;
import com.campus.reservation.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 预约操作日志：写入与查询 */
@Service
@RequiredArgsConstructor
public class ReservationLogService {

    private final ReservationLogMapper logMapper;
    private final SysUserMapper userMapper;

    /** 记录一次预约操作，自动补全操作用户的用户名与姓名 */
    public void record(Long userId, Long reservationId, Long resourceId, String resourceName,
                       int action, String detail) {
        ReservationLog log = new ReservationLog();
        log.setUserId(userId);
        if (userId != null) {
            SysUser user = userMapper.selectById(userId);
            if (user != null) {
                log.setUsername(user.getUsername());
                log.setNickname(user.getNickname());
            }
        }
        log.setReservationId(reservationId);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setAction(action);
        log.setDetail(detail);
        logMapper.insert(log);
    }

    /** 管理端：操作日志分页（可按用户名、操作类型筛选） */
    public Page<ReservationLog> page(Integer current, Integer size, String username, Integer action) {
        return logMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<ReservationLog>lambdaQuery()
                        .like(StringUtils.hasText(username), ReservationLog::getUsername, username)
                        .eq(action != null, ReservationLog::getAction, action)
                        .orderByDesc(ReservationLog::getCreatedAt));
    }
}

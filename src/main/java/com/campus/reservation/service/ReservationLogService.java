package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.ReservationLog;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.ReservationLogMapper;
import com.campus.reservation.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 预约操作日志：写入与查询 */
@Service
@RequiredArgsConstructor
public class ReservationLogService {

    private final ReservationLogMapper logMapper;
    private final SysUserMapper userMapper;

    /** 记录一次预约操作，自动补全预约归属用户与操作人信息 */
    public void record(Long userId, Long reservationId, Long resourceId, String resourceName,
                       int action, String detail) {
        insert(userId, reservationId, resourceId, resourceName, action, detail, false);
    }

    /** 记录一次系统自动操作（定时爽约、到期完成等），操作人固定为「系统自动」 */
    public void recordSystem(Long userId, Long reservationId, Long resourceId, String resourceName,
                             int action, String detail) {
        insert(userId, reservationId, resourceId, resourceName, action, detail, true);
    }

    private void insert(Long userId, Long reservationId, Long resourceId, String resourceName,
                        int action, String detail, boolean system) {
        ReservationLog log = new ReservationLog();
        log.setUserId(userId);
        if (userId != null) {
            SysUser user = userMapper.selectById(userId);
            if (user != null) {
                log.setUsername(user.getUsername());
                log.setNickname(user.getNickname());
            }
        }
        if (system) {
            log.setOperatorRole(ReservationLog.OPERATOR_SYSTEM);
            log.setOperatorName("系统自动");
        } else {
            fillOperator(log, userId);
        }
        log.setReservationId(reservationId);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setAction(action);
        log.setDetail(detail);
        logMapper.insert(log);
    }

    /**
     * 按当前登录上下文判定操作人：
     * 管理员代为操作的日志记到管理员名下，无登录上下文的（定时任务）算系统自动。
     */
    private void fillOperator(ReservationLog log, Long ownerId) {
        UserContext.LoginUser ctx = UserContext.get();
        if (ctx == null || ctx.getUserId() == null) {
            log.setOperatorRole(ReservationLog.OPERATOR_SYSTEM);
            log.setOperatorName("系统自动");
            return;
        }
        boolean admin = ctx.getRole() != null && ctx.getRole() == 1;
        log.setOperatorId(ctx.getUserId());
        log.setOperatorRole(admin ? ReservationLog.OPERATOR_ADMIN : ReservationLog.OPERATOR_USER);
        SysUser operator = userMapper.selectById(ctx.getUserId());
        if (operator != null) {
            log.setOperatorName(StringUtils.hasText(operator.getNickname())
                    ? operator.getNickname() : operator.getUsername());
        } else if (ctx.getUserId().equals(ownerId)) {
            log.setOperatorName(log.getNickname());
        }
    }

    /**
     * 某条预约的完整操作时间线（旧 → 新）。
     * 用户端预约详情侧拉框与违规详情共用，调用方必须先用 getOwned 做归属校验。
     */
    public List<ReservationLog> listByReservation(Long reservationId) {
        if (reservationId == null) {
            return List.of();
        }
        return logMapper.selectList(Wrappers.<ReservationLog>lambdaQuery()
                .eq(ReservationLog::getReservationId, reservationId)
                .orderByAsc(ReservationLog::getCreatedAt)
                .orderByAsc(ReservationLog::getId));
    }

    /**
     * 管理端：操作日志分页。
     * 可按用户名、操作类型、操作人角色筛选；传 reservationId 时只看某条预约的操作时间线。
     */
    public Page<ReservationLog> page(Integer current, Integer size, String username, Integer action,
                                     Integer operatorRole, Long reservationId) {
        return logMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<ReservationLog>lambdaQuery()
                        .like(StringUtils.hasText(username), ReservationLog::getUsername, username)
                        .eq(action != null, ReservationLog::getAction, action)
                        .eq(operatorRole != null, ReservationLog::getOperatorRole, operatorRole)
                        .eq(reservationId != null, ReservationLog::getReservationId, reservationId)
                        .orderByDesc(ReservationLog::getCreatedAt));
    }
}

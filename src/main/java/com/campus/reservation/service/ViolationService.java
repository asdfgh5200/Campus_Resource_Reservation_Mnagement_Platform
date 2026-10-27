package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ViolationDTO;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.ViolationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ViolationService {

    private final ViolationMapper violationMapper;
    private final SysUserMapper userMapper;

    public Page<Violation> page(Long userId, Integer current, Integer size) {
        return violationMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<Violation>lambdaQuery()
                        .eq(userId != null, Violation::getUserId, userId)
                        .orderByDesc(Violation::getCreatedAt));
    }

    /**
     * 管理端：按用户聚合的违规情况（每人一行，不按违规记录展开），支持按用户名/姓名搜索。
     */
    public Map<String, Object> userSummaryPage(String keyword, Integer current, Integer size) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        List<Violation> all = violationMapper.selectList(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getStatus, 1));
        Map<Long, List<Violation>> byUser = all.stream().collect(Collectors.groupingBy(Violation::getUserId));
        Map<Long, SysUser> users = byUser.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(byUser.keySet()).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<Violation>> e : byUser.entrySet()) {
            SysUser u = users.get(e.getKey());
            if (u == null) {
                continue;
            }
            if (kw != null) {
                String uname = u.getUsername() == null ? "" : u.getUsername().toLowerCase();
                String nick = u.getNickname() == null ? "" : u.getNickname().toLowerCase();
                if (!uname.contains(kw) && !nick.contains(kw)) {
                    continue;
                }
            }
            long today = e.getValue().stream()
                    .filter(v -> v.getCreatedAt() != null && !v.getCreatedAt().isBefore(todayStart))
                    .count();
            LocalDateTime last = e.getValue().stream()
                    .map(Violation::getCreatedAt).filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("totalCount", (long) e.getValue().size());
            m.put("todayCount", today);
            m.put("restricted", today >= ReservationService.DAILY_VIOLATION_LIMIT);
            m.put("lastViolationAt", last);
            rows.add(m);
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("totalCount"), (Long) a.get("totalCount")));

        int cur = current == null || current < 1 ? 1 : current;
        int sz = size == null || size < 1 ? 10 : size;
        int from = Math.min((cur - 1) * sz, rows.size());
        int to = Math.min(from + sz, rows.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", new ArrayList<>(rows.subList(from, to)));
        result.put("total", rows.size());
        result.put("limit", ReservationService.DAILY_VIOLATION_LIMIT);
        return result;
    }

    /** 我的违规概览：今日次数 / 累计次数 / 今日是否被禁止预约 / 取消次数与能否继续取消 */
    public Map<String, Object> mySummary(Long userId) {
        Long todayCount = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getStatus, 1)
                .ge(Violation::getCreatedAt, LocalDate.now().atStartOfDay()));
        Long totalCount = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getStatus, 1));
        SysUser user = userMapper.selectById(userId);
        int cancelCount = user == null || user.getCancelCount() == null ? 0 : user.getCancelCount();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todayCount", todayCount == null ? 0 : todayCount);
        m.put("totalCount", totalCount == null ? 0 : totalCount);
        m.put("limit", ReservationService.DAILY_VIOLATION_LIMIT);
        m.put("restricted", todayCount != null && todayCount >= ReservationService.DAILY_VIOLATION_LIMIT);
        m.put("cancelCount", cancelCount);
        m.put("cancelLimit", ReservationService.CANCEL_LIMIT);
        // 取消次数达上限后用户不能再自行取消预约，前端据此置灰按钮
        m.put("canCancel", cancelCount < ReservationService.CANCEL_LIMIT);
        return m;
    }

    /** 管理端：撤销（status=0）或恢复（status=1）一条违规记录 */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        UserContext.requireAdmin();
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.badRequest("违规状态不合法");
        }
        Violation violation = violationMapper.selectById(id);
        if (violation == null) {
            throw BizException.notFound("违规记录不存在");
        }
        violation.setStatus(status);
        violationMapper.updateById(violation);
    }

    /** 手动登记违规（管理员）：仅记录，不涉及信用分 */
    @Transactional
    public Violation add(ViolationDTO dto) {
        if (dto.getUserId() == null || dto.getType() == null) {
            throw BizException.badRequest("缺少必要参数");
        }
        SysUser user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        Violation violation = new Violation();
        violation.setUserId(dto.getUserId());
        violation.setReservationId(dto.getReservationId());
        violation.setType(dto.getType());
        violation.setDescription(dto.getDescription());
        violation.setDeductScore(0);
        violation.setStatus(1);
        violationMapper.insert(violation);
        return violation;
    }
}

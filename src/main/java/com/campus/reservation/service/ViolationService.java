package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.dto.ViolationDTO;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.ViolationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
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
            long week = e.getValue().stream()
                    .filter(v -> v.getCreatedAt() != null && v.getCreatedAt().isAfter(weekAgo))
                    .count();
            LocalDateTime last = e.getValue().stream()
                    .map(Violation::getCreatedAt).filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("creditScore", u.getCreditScore());
            m.put("totalCount", (long) e.getValue().size());
            m.put("weekCount", week);
            m.put("restricted", week >= ReservationService.WEEKLY_VIOLATION_LIMIT);
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
        result.put("limit", ReservationService.WEEKLY_VIOLATION_LIMIT);
        return result;
    }

    /** 我的违规概览：近 7 天次数 / 累计次数 / 是否被限制预约 */
    public Map<String, Object> mySummary(Long userId) {
        Long weekCount = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getStatus, 1)
                .ge(Violation::getCreatedAt, LocalDateTime.now().minusDays(7)));
        Long totalCount = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getStatus, 1));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weekCount", weekCount == null ? 0 : weekCount);
        m.put("totalCount", totalCount == null ? 0 : totalCount);
        m.put("limit", ReservationService.WEEKLY_VIOLATION_LIMIT);
        m.put("restricted", weekCount != null && weekCount >= ReservationService.WEEKLY_VIOLATION_LIMIT);
        return m;
    }

    /** 手动登记违规（管理员），并同步扣减信用分 */
    @Transactional
    public Violation add(ViolationDTO dto) {
        if (dto.getUserId() == null || dto.getType() == null) {
            throw BizException.badRequest("缺少必要参数（userId/type）");
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
        violation.setDeductScore(dto.getDeductScore() == null ? 0 : dto.getDeductScore());
        violation.setStatus(1);
        violationMapper.insert(violation);

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setCreditScore(Math.max(0,
                (user.getCreditScore() == null ? 100 : user.getCreditScore()) - violation.getDeductScore()));
        userMapper.updateById(update);
        return violation;
    }
}

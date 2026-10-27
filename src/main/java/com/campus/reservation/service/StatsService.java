package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.ViolationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    /** 角色：普通用户（用户维度统计只算这类账号） */
    private static final int ROLE_USER = 0;

    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final SysUserMapper userMapper;
    private final ViolationMapper violationMapper;

    /** 管理端数据概览 */
    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resourceTotal", resourceMapper.selectCount(null));
        data.put("resourceActive", resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().eq(Resource::getStatus, 1)));
        data.put("resourceRepair", resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().eq(Resource::getStatus, Resource.STATUS_REPAIRING)));
        data.put("resourceReported", resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().eq(Resource::getStatus, Resource.STATUS_REPORTED)));
        data.put("reservationTotal", reservationMapper.selectCount(null));
        data.put("reservationPending", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery().eq(Reservation::getStatus, Reservation.STATUS_PENDING)));
        data.put("reservationCompleted", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery().eq(Reservation::getStatus, Reservation.STATUS_COMPLETED)));

        List<Map<String, Object>> typeDistribution = new ArrayList<>();
        String[] names = {"教室", "实验室", "图书馆", "体育馆"};
        for (int t = 1; t <= 4; t++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", names[t - 1]);
            m.put("value", resourceMapper.selectCount(Wrappers.<Resource>lambdaQuery().eq(Resource::getResourceType, t)));
            typeDistribution.add(m);
        }
        data.put("typeDistribution", typeDistribution);
        data.put("statusDistribution", statusDistribution());
        data.put("trend", weeklyTrend());
        data.put("hourDistribution", hourDistribution());
        data.put("hotResources", hotResources());

        // 用户维度：与资源维度并列，构成「资源 + 用户」整合概览
        data.put("userTotal", userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, ROLE_USER)));
        data.put("userNewToday", userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, ROLE_USER)
                .ge(SysUser::getCreatedAt, LocalDate.now().atStartOfDay())));
        data.put("userActive", activeUserCount());
        data.put("userViolation", violatedUserCount());
        data.put("userTrend", weeklyUserTrend());
        return data;
    }

    /** 当前有待打卡/使用中预约的去重用户数 */
    private Long activeUserCount() {
        return (long) reservationMapper.selectMaps(new QueryWrapper<Reservation>()
                .select("DISTINCT user_id AS userId")
                .in("status", Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE)).size();
    }

    /** 存在有效违规记录的去重用户数 */
    private Long violatedUserCount() {
        return (long) violationMapper.selectMaps(new QueryWrapper<Violation>()
                .select("DISTINCT user_id AS userId")
                .eq("status", 1)).size();
    }

    /** 近 7 天（含今天）每天的新增注册数与该日下单用户数 */
    private List<Map<String, Object>> weeklyUserTrend() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        List<SysUser> newUsers = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getCreatedAt)
                .eq(SysUser::getRole, ROLE_USER)
                .ge(SysUser::getCreatedAt, from.atStartOfDay()));
        Map<LocalDate, Long> regCounts = newUsers.stream()
                .filter(u -> u.getCreatedAt() != null)
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate(), Collectors.counting()));

        List<Reservation> orders = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .select(Reservation::getCreatedAt, Reservation::getUserId)
                .ge(Reservation::getCreatedAt, from.atStartOfDay()));
        Map<LocalDate, Set<Long>> activeByDay = new HashMap<>();
        for (Reservation r : orders) {
            if (r.getCreatedAt() == null || r.getUserId() == null) {
                continue;
            }
            activeByDay.computeIfAbsent(r.getCreatedAt().toLocalDate(), d -> new HashSet<>())
                    .add(r.getUserId());
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = from.plusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("label", day.format(fmt));
            m.put("register", regCounts.getOrDefault(day, 0L));
            m.put("active", (long) activeByDay.getOrDefault(day, Set.of()).size());
            result.add(m);
        }
        return result;
    }

    /** 预约时段热度：按使用开始时间的小时统计（07:00 - 22:00，无数据的时段补 0） */
    private List<Map<String, Object>> hourDistribution() {
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .select(Reservation::getStartTime)
                .isNotNull(Reservation::getStartTime));
        Map<Integer, Long> counts = list.stream()
                .filter(r -> r.getStartTime() != null)
                .collect(Collectors.groupingBy(r -> r.getStartTime().getHour(), Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 7; h <= 22; h++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", h);
            m.put("label", String.format("%02d:00", h));
            m.put("value", counts.getOrDefault(h, 0L));
            result.add(m);
        }
        return result;
    }

    /** 近 7 天（含今天）每天新建的预约数 */
    private List<Map<String, Object>> weeklyTrend() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        List<Reservation> created = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .select(Reservation::getCreatedAt)
                .ge(Reservation::getCreatedAt, from.atStartOfDay()));
        Map<LocalDate, Long> counts = created.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate(), Collectors.counting()));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = from.plusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("label", day.format(fmt));
            m.put("value", counts.getOrDefault(day, 0L));
            result.add(m);
        }
        return result;
    }

    /** 预约状态构成（固定 6 种，缺的补 0） */
    private List<Map<String, Object>> statusDistribution() {
        List<Map<String, Object>> rows = reservationMapper.selectMaps(new QueryWrapper<Reservation>()
                .select("status", "COUNT(*) AS total")
                .groupBy("status"));
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row.get("status") instanceof Number s && row.get("total") instanceof Number t) {
                counts.put(s.intValue(), t.longValue());
            }
        }
        String[] names = {"待打卡", "履约中", "已完成", "已取消", "爽约", "违规"};
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", names[i]);
            m.put("status", i + 1);
            m.put("value", counts.getOrDefault(i + 1, 0L));
            result.add(m);
        }
        return result;
    }

    /** 累计预约次数最多的 5 个资源 */
    private List<Map<String, Object>> hotResources() {
        List<Map<String, Object>> rows = reservationMapper.selectMaps(new QueryWrapper<Reservation>()
                .select("resource_id AS resourceId", "COUNT(*) AS total")
                .isNotNull("resource_id")
                .groupBy("resource_id")
                .orderByDesc("total")
                .last("LIMIT 5"));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream()
                .map(r -> ((Number) r.get("resourceId")).longValue())
                .toList();
        Map<Long, Resource> resources = resourceMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Resource::getId, r -> r));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long resourceId = ((Number) row.get("resourceId")).longValue();
            Resource res = resources.get(resourceId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resourceId", resourceId);
            m.put("name", res == null ? "未知资源" : res.getName());
            m.put("type", res == null ? null : res.getResourceType());
            m.put("count", ((Number) row.get("total")).longValue());
            result.add(m);
        }
        return result;
    }
}

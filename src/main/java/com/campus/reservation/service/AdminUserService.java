package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.entity.UserMessage;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.UserMessageMapper;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端「用户管理」：普通用户列表（含预约状态 / 取消次数 / 违规统计）与用户详情。
 *
 * <p>详情覆盖用户在用户端能看到的全部内容：资料与数值（可改）、预约记录（可改状态）、
 * 违规记录（可撤销/恢复）、留言与通知（可回复/推送）；三类记录都支持按时间筛选。
 * 原「留言管理」页已并入这里，管理员账号不进用户档案。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /** 详情页每类明细最多返回的条数 */
    private static final int DETAIL_LIMIT = 50;

    private final SysUserMapper userMapper;
    private final ReservationMapper reservationMapper;
    private final ViolationMapper violationMapper;
    private final ResourceMapper resourceMapper;
    private final UserMessageMapper messageMapper;
    private final MessageService messageService;

    /** 用户分页：仅普通用户，含预约数、当前预约状态、取消次数、今日/累计违规、是否今日受限 */
    public Map<String, Object> page(String keyword, Integer current, Integer size) {
        UserContext.requireAdmin();
        int cur = current == null || current < 1 ? 1 : current;
        int sz = size == null || size < 1 ? 10 : size;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        // 管理员账号不进用户档案：用户端能看到的东西才有必要在管理端维护
        Page<SysUser> userPage = userMapper.selectPage(new Page<>(cur, sz),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getRole, 0)
                        .and(kw != null, w -> w.like(SysUser::getUsername, kw).or().like(SysUser::getNickname, kw))
                        .orderByAsc(SysUser::getId));
        List<SysUser> users = userPage.getRecords();
        List<Long> userIds = users.stream().map(SysUser::getId).toList();

        // 批量取本页用户的违规情况 / 当前进行中的预约，避免 N+1
        Map<Long, List<Violation>> violations = userIds.isEmpty() ? Map.of()
                : violationMapper.selectList(Wrappers.<Violation>lambdaQuery()
                        .in(Violation::getUserId, userIds)
                        .eq(Violation::getStatus, 1)).stream()
                .collect(Collectors.groupingBy(Violation::getUserId));
        Map<Long, Reservation> active = activeReservations(userIds);
        // 待回复留言：用户端发来但还没有管理员回复的条数，列表上用红点提示
        Map<Long, Long> pendingAsks = pendingAskCounts(userIds);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser u : users) {
            List<Violation> vs = violations.getOrDefault(u.getId(), List.of());
            long today = vs.stream()
                    .filter(v -> v.getCreatedAt() != null && !v.getCreatedAt().isBefore(todayStart))
                    .count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("role", u.getRole());
            m.put("status", u.getStatus());
            m.put("phone", u.getPhone());
            m.put("email", u.getEmail());
            m.put("cancelCount", u.getCancelCount() == null ? 0 : u.getCancelCount());
            m.put("reservationTotal", u.getReservationTotal() == null ? 0 : u.getReservationTotal());
            m.put("restrictOverride", u.getRestrictOverride() == null ? 0 : u.getRestrictOverride());
            m.put("createdAt", u.getCreatedAt());
            m.put("reservationCount", u.getReservationTotal() == null ? 0 : u.getReservationTotal());
            m.put("reservationState", stateOf(active.get(u.getId())));
            m.put("todayCount", today);
            m.put("totalCount", (long) vs.size());
            m.put("restricted", restrictedOf(u, today));
            m.put("pendingAsks", pendingAsks.getOrDefault(u.getId(), 0L));
            m.put("lastViolationAt", vs.stream().map(Violation::getCreatedAt)
                    .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));
            rows.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", userPage.getTotal());
        result.put("limit", ReservationService.DAILY_VIOLATION_LIMIT);
        result.put("cancelLimit", ReservationService.CANCEL_LIMIT);
        return result;
    }

    /**
     * 用户详情：资料 + 统计 + 预约记录 + 违规记录 + 留言/通知。
     * startDate / endDate 为空表示不限时间，非空时按自然日闭区间筛选（预约按占用时段判定重叠）。
     */
    public Map<String, Object> detail(Long id, LocalDate startDate, LocalDate endDate) {
        UserContext.requireAdmin();
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        LocalDateTime from = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime to = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

        // 违规记录：保留已撤销的记录，管理员可以恢复
        List<Violation> violations = violationMapper.selectList(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, id)
                .ge(from != null, Violation::getCreatedAt, from)
                .lt(to != null, Violation::getCreatedAt, to)
                .orderByDesc(Violation::getCreatedAt)
                .last("LIMIT " + DETAIL_LIMIT));

        // 预约记录：按占用时段与筛选区间是否重叠判断
        List<Reservation> reservations = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, id)
                .lt(to != null, Reservation::getStartTime, to)
                .gt(from != null, Reservation::getEndTime, from)
                .orderByDesc(Reservation::getStartTime)
                .last("LIMIT " + DETAIL_LIMIT));
        fillResources(reservations);

        List<Map<String, Object>> messages = messageRows(id, from, to);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long todayCount = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, id)
                .eq(Violation::getStatus, 1)
                .ge(Violation::getCreatedAt, todayStart));
        Long totalViolation = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, id)
                .eq(Violation::getStatus, 1));
        long today = todayCount == null ? 0 : todayCount;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("reservationCount", user.getReservationTotal() == null ? 0 : user.getReservationTotal());
        stats.put("todayViolations", today);
        stats.put("totalViolations", totalViolation == null ? 0 : totalViolation);
        stats.put("limit", ReservationService.DAILY_VIOLATION_LIMIT);
        stats.put("restricted", restrictedOf(user, today));
        stats.put("restrictOverride", user.getRestrictOverride() == null ? 0 : user.getRestrictOverride());
        stats.put("cancelCount", user.getCancelCount() == null ? 0 : user.getCancelCount());
        stats.put("cancelLimit", ReservationService.CANCEL_LIMIT);
        stats.put("reservationState", stateOf(activeReservations(List.of(id)).get(id)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        result.put("stats", stats);
        result.put("reservations", reservations);
        result.put("violations", violations);
        result.put("messages", messages);
        return result;
    }

    /**
     * 管理端修改用户资料与数值：姓名、手机号、邮箱、账号状态、取消次数、累计预约、违规次数、预约权限。
     * 违规次数是违规表的真实记录数，因此按目标值补记/撤销记录，保证列表与统计一致；
     * 预约权限存入覆盖位（0 按规则、1 强制正常、2 强制受限），会被预约创建时读取。
     */
    @Transactional
    public SysUser update(Long id, Map<String, Object> body) {
        UserContext.requireAdmin();
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        SysUser patch = new SysUser();
        patch.setId(id);
        if (body.containsKey("nickname")) {
            String nickname = text(body.get("nickname"));
            if (!StringUtils.hasText(nickname)) {
                throw BizException.badRequest("姓名不能为空");
            }
            patch.setNickname(nickname);
        }
        if (body.containsKey("phone")) {
            patch.setPhone(text(body.get("phone")));
        }
        if (body.containsKey("email")) {
            patch.setEmail(text(body.get("email")));
        }
        if (body.containsKey("status")) {
            Integer status = number(body.get("status"));
            if (status == null || (status != 0 && status != 1)) {
                throw BizException.badRequest("账号状态不合法");
            }
            patch.setStatus(status);
        }
        if (body.containsKey("cancelCount")) {
            Integer cancelCount = number(body.get("cancelCount"));
            if (cancelCount == null || cancelCount < 0 || cancelCount > ReservationService.CANCEL_LIMIT) {
                throw BizException.badRequest("取消次数需在 0 ~ " + ReservationService.CANCEL_LIMIT + " 之间");
            }
            patch.setCancelCount(cancelCount);
        }
        if (body.containsKey("reservationTotal")) {
            Integer total = number(body.get("reservationTotal"));
            if (total == null || total < 0) {
                throw BizException.badRequest("累计预约次数不能为负");
            }
            patch.setReservationTotal(total);
        }
        if (body.containsKey("restrictOverride")) {
            Integer override = number(body.get("restrictOverride"));
            if (override == null || override < 0 || override > 2) {
                throw BizException.badRequest("预约权限不合法");
            }
            patch.setRestrictOverride(override);
        }
        userMapper.updateById(patch);
        // 违规次数：编辑后补齐/撤销真实记录，避免统计与记录列表对不上
        if (body.containsKey("todayViolations") || body.containsKey("totalViolations")) {
            int total = body.containsKey("totalViolations")
                    ? Objects.requireNonNullElse(number(body.get("totalViolations")), -1)
                    : activeViolationCount(id, null);
            int today = body.containsKey("todayViolations")
                    ? Objects.requireNonNullElse(number(body.get("todayViolations")), -1)
                    : activeViolationCount(id, LocalDate.now().atStartOfDay());
            if (total < 0 || today < 0) {
                throw BizException.badRequest("违规次数不合法");
            }
            reconcileViolations(id, today, total);
        }
        return userMapper.selectById(id);
    }

    /** 预约权限：覆盖位优先，未覆盖时按当日违规次数自动判定 */
    private static boolean restrictedOf(SysUser user, long todayViolations) {
        Integer override = user.getRestrictOverride();
        if (override != null && override == 2) {
            return true;
        }
        if (override != null && override == 1) {
            return false;
        }
        return todayViolations >= ReservationService.DAILY_VIOLATION_LIMIT;
    }

    /** 统计有效违规记录数；from 非空时只统计该时间点之后的记录 */
    private int activeViolationCount(Long userId, LocalDateTime from) {
        Long count = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getStatus, 1)
                .ge(from != null, Violation::getCreatedAt, from));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 把有效违规记录调整到「今日 today 条 / 合计 total 条」。
     * 多余的记录置为已撤销（可在违规列表里恢复），不足的补记「管理员补记」；
     * 补记的非今日记录时间落在昨天，避免影响今日次数。
     */
    private void reconcileViolations(Long userId, int targetToday, int targetTotal) {
        if (targetToday > targetTotal) {
            throw BizException.badRequest("今日违规不能大于累计违规");
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<Violation> active = violationMapper.selectList(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getStatus, 1)
                .orderByDesc(Violation::getCreatedAt));
        List<Violation> todayRows = active.stream()
                .filter(v -> v.getCreatedAt() != null && !v.getCreatedAt().isBefore(todayStart)).toList();
        List<Violation> otherRows = active.stream()
                .filter(v -> v.getCreatedAt() == null || v.getCreatedAt().isBefore(todayStart)).toList();

        // 今日：多余的撤销，不足的补记（时间取当前，计入今日）
        for (int i = targetToday; i < todayRows.size(); i++) {
            revokeViolation(todayRows.get(i).getId());
        }
        for (int i = todayRows.size(); i < targetToday; i++) {
            insertManualViolation(userId, LocalDateTime.now());
        }
        // 其余：同理，补记时间取昨天（不计入今日）
        int targetOther = targetTotal - targetToday;
        for (int i = targetOther; i < otherRows.size(); i++) {
            revokeViolation(otherRows.get(i).getId());
        }
        for (int i = otherRows.size(); i < targetOther; i++) {
            insertManualViolation(userId, todayStart.minusDays(1).plusHours(20));
        }
    }

    private void revokeViolation(Long id) {
        Violation patch = new Violation();
        patch.setId(id);
        patch.setStatus(0);
        violationMapper.updateById(patch);
    }

    private void insertManualViolation(Long userId, LocalDateTime createdAt) {
        Violation v = new Violation();
        v.setUserId(userId);
        v.setType(Violation.TYPE_OTHER);
        v.setDescription("违规记录已由平台核实登记");
        v.setDeductScore(0);
        v.setStatus(1);
        violationMapper.insert(v);
        // createdAt 由自动填充写入，补记需要指定日期时单独回写
        if (createdAt != null) {
            violationMapper.update(null, Wrappers.<Violation>lambdaUpdate()
                    .eq(Violation::getId, v.getId())
                    .set(Violation::getCreatedAt, createdAt));
        }
    }

    /** 管理端：向指定用户推送一条站内通知（用户端「消息中心」可见） */
    @Transactional
    public void notify(Long id, String content) {
        UserContext.requireAdmin();
        messageService.adminNotify(id, content);
    }

    /** 各用户「待回复」留言数：用户发来、但还没有任何管理员回复的留言条数 */
    private Map<Long, Long> pendingAskCounts(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserMessage> asks = messageMapper.selectList(Wrappers.<UserMessage>lambdaQuery()
                .in(UserMessage::getUserId, userIds)
                .eq(UserMessage::getDirection, UserMessage.DIRECTION_USER_TO_ADMIN));
        if (asks.isEmpty()) {
            return Map.of();
        }
        Set<Long> repliedIds = messageMapper.selectList(Wrappers.<UserMessage>lambdaQuery()
                        .in(UserMessage::getUserId, userIds)
                        .eq(UserMessage::getDirection, UserMessage.DIRECTION_ADMIN_TO_USER)
                        .isNotNull(UserMessage::getRefId)).stream()
                .map(UserMessage::getRefId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return asks.stream()
                .filter(a -> !repliedIds.contains(a.getId()))
                .collect(Collectors.groupingBy(UserMessage::getUserId, Collectors.counting()));
    }

    /** 该用户的留言与通知（双向），附「是否已回复」标记 */
    private List<Map<String, Object>> messageRows(Long userId, LocalDateTime from, LocalDateTime to) {
        List<UserMessage> messages = messageMapper.selectList(Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getUserId, userId)
                .ge(from != null, UserMessage::getCreatedAt, from)
                .lt(to != null, UserMessage::getCreatedAt, to)
                .orderByDesc(UserMessage::getCreatedAt)
                .last("LIMIT " + DETAIL_LIMIT));
        List<Long> askIds = messages.stream()
                .filter(m -> m.getDirection() != null
                        && m.getDirection() == UserMessage.DIRECTION_USER_TO_ADMIN)
                .map(UserMessage::getId)
                .toList();
        Set<Long> repliedAskIds = askIds.isEmpty() ? Set.of()
                : messageMapper.selectList(Wrappers.<UserMessage>lambdaQuery()
                        .eq(UserMessage::getUserId, userId)
                        .eq(UserMessage::getDirection, UserMessage.DIRECTION_ADMIN_TO_USER)
                        .in(UserMessage::getRefId, askIds)).stream()
                .map(UserMessage::getRefId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return messages.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("direction", m.getDirection());
            row.put("content", m.getContent());
            row.put("isRead", m.getIsRead());
            row.put("refId", m.getRefId());
            row.put("createdAt", m.getCreatedAt());
            row.put("replied", m.getDirection() != null
                    && m.getDirection() == UserMessage.DIRECTION_USER_TO_ADMIN
                    && repliedAskIds.contains(m.getId()));
            return row;
        }).toList();
    }

    /** 用户当前进行中的预约：同一用户取优先级最高的那条（履约中 > 预约中） */
    private Map<Long, Reservation> activeReservations(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getUserId, userIds)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .ge(Reservation::getEndTime, LocalDateTime.now())
                .orderByAsc(Reservation::getStartTime));
        Map<Long, Reservation> map = new LinkedHashMap<>();
        for (Reservation r : list) {
            Reservation old = map.get(r.getUserId());
            if (old == null || stateRank(r) > stateRank(old)) {
                map.put(r.getUserId(), r);
            }
        }
        return map;
    }

    private static int stateRank(Reservation r) {
        if (r.getStatus() != null && r.getStatus() == Reservation.STATUS_IN_USE) {
            return 2;
        }
        return 1;
    }

    /** 预约状态：none 未预约、pending 预约中、in_use 履约中 */
    private static String stateOf(Reservation r) {
        if (r == null || r.getStatus() == null) {
            return "none";
        }
        if (r.getStatus() == Reservation.STATUS_IN_USE) {
            return "in_use";
        }
        return r.getStatus() == Reservation.STATUS_PENDING ? "pending" : "none";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Integer number(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void fillResources(List<Reservation> reservations) {
        if (reservations.isEmpty()) {
            return;
        }
        List<Long> ids = reservations.stream().map(Reservation::getResourceId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Resource> map = ids.isEmpty() ? Map.of()
                : resourceMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(Resource::getId, Function.identity()));
        reservations.forEach(r -> r.setResource(map.get(r.getResourceId())));
    }
}

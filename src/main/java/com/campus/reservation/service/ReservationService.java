package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ReservationDTO;
import com.campus.reservation.entity.Holiday;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.ReservationLog;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.entity.Violation;
import com.campus.reservation.mapper.HolidayMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.mapper.ViolationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    /** 信用分低于该值限制预约 */
    public static final int CREDIT_THRESHOLD = 60;
    /** 一周内违规次数达到该值，本周内禁止预约 */
    public static final int WEEKLY_VIOLATION_LIMIT = 3;
    /** 可提前打卡的分钟数（预约开始前） */
    public static final int CHECKIN_ADVANCE_MINUTES = 30;
    /** 开始后仍可打卡的分钟数，超过则自动爽约并释放空间 */
    public static final int CHECKIN_LATE_MINUTES = 5;
    /** 30 天内取消次数达到该值记恶意取消 */
    public static final int CANCEL_LIMIT = 3;
    /** 普通暂离时长（分钟） */
    public static final int AWAY_MINUTES = 30;
    /** 饭点暂离时长（分钟） */
    public static final int AWAY_MEAL_MINUTES = 60;
    /** 午餐暂离时段 */
    private static final LocalTime MEAL_LUNCH_START = LocalTime.of(11, 0);
    private static final LocalTime MEAL_LUNCH_END = LocalTime.of(12, 30);
    /** 晚餐暂离时段 */
    private static final LocalTime MEAL_DINNER_START = LocalTime.of(17, 30);
    private static final LocalTime MEAL_DINNER_END = LocalTime.of(19, 0);

    /** 操作日志中时间的展示格式 */
    private static final DateTimeFormatter LOG_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReservationMapper reservationMapper;
    private final ResourceMapper resourceMapper;
    private final SysUserMapper userMapper;
    private final HolidayMapper holidayMapper;
    private final ViolationMapper violationMapper;
    private final ReservationLogService logService;
    private final CheckinCodeService checkinCodeService;

    /**
     * 创建预约：规则校验（信用分/资源状态/节假日/开放时间）+ 时间冲突检测（预约时长不设上限）。
     * 冲突判定采用半开区间 [start, end)：newStart < existEnd AND newEnd > existStart。
     */
    @Transactional
    public Reservation create(ReservationDTO dto) {
        Long userId = UserContext.requireUserId();
        if (dto.getResourceId() == null || dto.getStartTime() == null || dto.getEndTime() == null) {
            throw BizException.badRequest("缺少必要参数（resourceId/startTime/endTime）");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("账号不可用");
        }
        if (user.getCreditScore() != null && user.getCreditScore() < CREDIT_THRESHOLD) {
            throw BizException.forbidden("信用分不足，暂无法预约");
        }
        // 一周内违规达 3 次 → 本周内禁止预约
        Long weekViolations = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getUserId, userId)
                .eq(Violation::getStatus, 1)
                .ge(Violation::getCreatedAt, LocalDateTime.now().minusDays(7)));
        if (weekViolations != null && weekViolations >= WEEKLY_VIOLATION_LIMIT) {
            throw BizException.forbidden(String.format(
                    "近 7 天内已有 %d 次违规记录，本周内暂无法预约", weekViolations));
        }
        Resource resource = resourceMapper.selectById(dto.getResourceId());
        if (resource == null) {
            throw BizException.notFound("资源不存在");
        }
        if (resource.getStatus() == 3) {
            throw BizException.conflict("该资源正在维修中，暂不可预约");
        }
        if (resource.getStatus() == 2) {
            throw BizException.conflict("该资源正在被使用");
        }
        if (resource.getResourceType() != null && resource.getResourceType() == 1) {
            throw BizException.badRequest("教室无需预约，未被课程占用时可直接使用");
        }

        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = dto.getEndTime();
        if (!start.isBefore(end)) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        if (start.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw BizException.badRequest("预约开始时间不能早于当前时间");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw BizException.badRequest("暂不支持跨天预约");
        }
        if (start.toLocalTime().isBefore(resource.getOpenTime())
                || end.toLocalTime().isAfter(resource.getCloseTime())) {
            throw BizException.badRequest("预约时段需在开放时间内（" + resource.getOpenTime() + " - " + resource.getCloseTime() + "）");
        }

        // 同一用户在**同一时间段**内只能有一个进行中的预约（跨资源按时间冲突判定；不冲突可多次预约）
        List<Reservation> mine = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, end)
                .gt(Reservation::getEndTime, start));
        if (!mine.isEmpty()) {
            Reservation other = mine.get(0);
            Resource otherRes = resourceMapper.selectById(other.getResourceId());
            throw BizException.conflict(String.format("该时间段你已有预约：%s %s ~ %s，同一时间只能预约一个资源",
                    otherRes == null ? "其他资源" : otherRes.getName(),
                    other.getStartTime().format(LOG_TIME_FMT), other.getEndTime().format(LOG_TIME_FMT)));
        }

        // 节假日校验
        LocalDate date = start.toLocalDate();
        Long holidayCnt = holidayMapper.selectCount(Wrappers.<Holiday>lambdaQuery()
                .le(Holiday::getStartDate, date)
                .ge(Holiday::getEndDate, date));
        if (holidayCnt > 0) {
            throw BizException.conflict("节假日暂停预约");
        }

        // 时间冲突检测：体育馆允许多人同时预约（按时段人数之和校验），其他资源不允许时段重叠
        boolean isGym = resource.getResourceType() != null && resource.getResourceType() == 4;
        int headcount = dto.getHeadcount() == null ? 1 : dto.getHeadcount();
        if (headcount < 1) {
            throw BizException.badRequest("使用人数至少为 1 人");
        }
        List<Reservation> overlapping = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getResourceId, dto.getResourceId())
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, end)
                .gt(Reservation::getEndTime, start));
        if (isGym) {
            int capacity = resource.getCapacity() == null ? 0 : resource.getCapacity();
            int occupied = overlapping.stream()
                    .mapToInt(r -> r.getHeadcount() == null ? 1 : r.getHeadcount())
                    .sum();
            if (occupied + headcount > capacity) {
                String unit = resource.getUnit() == null ? "人" : resource.getUnit();
                throw BizException.conflict(String.format(
                        "该时段容量不足：已约 %d %s，总容量 %d %s，剩余 %d %s",
                        occupied, unit, capacity, unit, Math.max(0, capacity - occupied), unit));
            }
        } else {
            if (!overlapping.isEmpty()) {
                throw BizException.conflict("预约时间与已有预约冲突");
            }
            // 非体育馆资源按"一次预约=一个空间"处理
            headcount = 1;
        }

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setResourceId(dto.getResourceId());
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setCheckinType(2);
        reservation.setHeadcount(headcount);
        reservation.setCheckinFlag(0);
        reservation.setStatus(Reservation.STATUS_PENDING);
        reservation.setRemark(dto.getRemark());
        reservationMapper.insert(reservation);
        reservation.setResource(resource);
        writeLog(userId, reservation.getId(), dto.getResourceId(), ReservationLog.ACTION_CREATE,
                String.format("创建预约：%s %s ~ %s", resource.getName(), start.format(LOG_TIME_FMT), end.format(LOG_TIME_FMT)));
        return reservation;
    }

    /**
     * 懒惰状态流转：
     * 1) 超过打卡时限（开始后 5 分钟）仍未打卡 → 爽约（记违规扣信用分）并释放空间；
     * 2) 暂离超时未返回 → 自动释放空间（记违规扣信用分）；
     * 3) 到期未结束 → 已完成。
     */
    public void refreshStatuses() {
        LocalDateTime now = LocalDateTime.now();

        List<Reservation> noShows = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Reservation.STATUS_PENDING)
                .lt(Reservation::getStartTime, now.minusMinutes(CHECKIN_LATE_MINUTES)));
        for (Reservation r : noShows) {
            r.setStatus(Reservation.STATUS_NOSHOW);
            reservationMapper.updateById(r);
            recordViolation(r.getUserId(), r.getId(), Violation.TYPE_NOSHOW,
                    "超过打卡时限未完成打卡，记一次爽约", 10);
            writeLog(r.getUserId(), r.getId(), r.getResourceId(), ReservationLog.ACTION_NOSHOW,
                    String.format("超过打卡时限（开始后 %d 分钟内未打卡），系统自动释放空间并记为爽约", CHECKIN_LATE_MINUTES));
        }

        List<Reservation> awayTimeouts = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Reservation.STATUS_IN_USE)
                .isNotNull(Reservation::getAwayUntil)
                .lt(Reservation::getAwayUntil, now));
        for (Reservation r : awayTimeouts) {
            r.setStatus(Reservation.STATUS_COMPLETED);
            r.setAwayUntil(null);
            reservationMapper.updateById(r);
            recordViolation(r.getUserId(), r.getId(), Violation.TYPE_OVERTIME,
                    "暂离超时未返回，记一次违规", 5);
            writeLog(r.getUserId(), r.getId(), r.getResourceId(), ReservationLog.ACTION_AWAY_TIMEOUT,
                    "暂离超时未返回，系统自动释放空间并记违规一次");
        }

        List<Reservation> finished = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Reservation.STATUS_IN_USE)
                .lt(Reservation::getEndTime, now));
        for (Reservation r : finished) {
            r.setStatus(Reservation.STATUS_COMPLETED);
            r.setAwayUntil(null);
            reservationMapper.updateById(r);
            writeLog(r.getUserId(), r.getId(), r.getResourceId(), ReservationLog.ACTION_COMPLETE,
                    "预约到期，系统自动标记为已完成");
        }
    }

    /** 是否处于饭点暂离时段（午餐 11:00-12:30、晚餐 17:30-19:00） */
    public static boolean isMealTime(LocalTime time) {
        return (!time.isBefore(MEAL_LUNCH_START) && !time.isAfter(MEAL_LUNCH_END))
                || (!time.isBefore(MEAL_DINNER_START) && !time.isAfter(MEAL_DINNER_END));
    }

    /** 提前释放：使用中的预约主动结束，立即释放空间 */
    @Transactional
    public Reservation release(Long id) {
        Reservation reservation = getOwned(id);
        if (reservation.getStatus() != Reservation.STATUS_IN_USE) {
            throw BizException.badRequest("仅使用中的预约可提前释放");
        }
        reservation.setStatus(Reservation.STATUS_COMPLETED);
        reservation.setAwayUntil(null);
        reservationMapper.updateById(reservation);
        fillResources(List.of(reservation));
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_RELEASE, "提前释放空间，预约结束");
        return reservation;
    }

    /** 暂离：保留空间，minutes 分钟内返回；饭点可暂离 1 小时 */
    @Transactional
    public Reservation away(Long id, Integer minutes) {
        Reservation reservation = getOwned(id);
        if (reservation.getStatus() != Reservation.STATUS_IN_USE) {
            throw BizException.badRequest("仅使用中的预约可暂离");
        }
        int m = minutes == null ? AWAY_MINUTES : minutes;
        if (m != AWAY_MINUTES && m != AWAY_MEAL_MINUTES) {
            throw BizException.badRequest("暂离时长仅支持 30 分钟或 60 分钟");
        }
        if (m == AWAY_MEAL_MINUTES && !isMealTime(LocalTime.now())) {
            throw BizException.badRequest("仅午餐（11:00-12:30）或晚餐（17:30-19:00）时段可暂离 1 小时");
        }
        reservation.setAwayUntil(LocalDateTime.now().plusMinutes(m));
        reservationMapper.updateById(reservation);
        fillResources(List.of(reservation));
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_AWAY,
                String.format("暂离 %d 分钟，暂离期间空间保留（超时未返回将自动释放并记违规）", m));
        return reservation;
    }

    /** 结束暂离，恢复使用 */
    @Transactional
    public Reservation cancelAway(Long id) {
        Reservation reservation = getOwned(id);
        if (reservation.getStatus() != Reservation.STATUS_IN_USE || reservation.getAwayUntil() == null) {
            throw BizException.badRequest("当前未处于暂离状态");
        }
        reservation.setAwayUntil(null);
        reservationMapper.updateById(reservation);
        fillResources(List.of(reservation));
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_AWAY, "结束暂离，返回使用");
        return reservation;
    }

    public Page<Reservation> my(Integer current, Integer size, Integer status) {
        refreshStatuses();
        Page<Reservation> page = reservationMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getUserId, UserContext.requireUserId())
                        .eq(status != null, Reservation::getStatus, status)
                        .orderByDesc(Reservation::getCreatedAt));
        fillResources(page.getRecords());
        return page;
    }

    /** 查询某资源某日已占用的预约（待打卡 + 使用中），用于前端时段条标红 */
    public List<Reservation> listByResource(Long resourceId, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        return reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getResourceId, resourceId)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, dayEnd)
                .gt(Reservation::getEndTime, dayStart)
                .orderByAsc(Reservation::getStartTime));
    }

    public Reservation detail(Long id) {
        refreshStatuses();
        Reservation reservation = getOwned(id);
        fillResources(List.of(reservation));
        return reservation;
    }

    @Transactional
    public Reservation cancel(Long id) {
        Reservation reservation = getOwned(id);
        if (reservation.getStatus() != Reservation.STATUS_PENDING) {
            throw BizException.badRequest("当前状态不可取消");
        }
        reservation.setStatus(Reservation.STATUS_CANCELLED);
        reservationMapper.updateById(reservation);
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_CANCEL,
                String.format("取消预约：%s ~ %s",
                        reservation.getStartTime().format(LOG_TIME_FMT), reservation.getEndTime().format(LOG_TIME_FMT)));

        // 30 天内取消次数过多 → 记恶意取消并扣信用分
        Long cancelCnt = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, reservation.getUserId())
                .eq(Reservation::getStatus, Reservation.STATUS_CANCELLED)
                .ge(Reservation::getCreatedAt, LocalDateTime.now().minusDays(30)));
        if (cancelCnt >= CANCEL_LIMIT) {
            recordViolation(reservation.getUserId(), id, Violation.TYPE_BAD_CANCEL,
                    "30 天内取消预约达 " + cancelCnt + " 次，记恶意取消", 5);
        }
        return reservation;
    }

    /**
     * 删除预约记录（用户端「我的预约」自定义清理）。
     * 仅允许删除**已结束**的预约（已完成 / 已取消 / 爽约 / 违规）；
     * 待打卡、使用中的预约需先取消或提前释放，避免误删进行中的预约。
     */
    @Transactional
    public void delete(Long id) {
        refreshStatuses();
        Reservation reservation = getOwned(id);
        int status = reservation.getStatus();
        if (status == Reservation.STATUS_PENDING || status == Reservation.STATUS_IN_USE) {
            throw BizException.badRequest("进行中的预约不可删除，请先取消或提前释放");
        }
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_DELETE,
                String.format("删除预约记录：%s ~ %s",
                        reservation.getStartTime().format(LOG_TIME_FMT), reservation.getEndTime().format(LOG_TIME_FMT)));
        reservationMapper.deleteById(id);
    }

    @Transactional
    public Reservation checkin(Long id) {
        Reservation reservation = getOwned(id);
        if (reservation.getStatus() != Reservation.STATUS_PENDING) {
            throw BizException.badRequest("当前状态不可打卡");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(reservation.getStartTime().minusMinutes(CHECKIN_ADVANCE_MINUTES))) {
            throw BizException.badRequest(String.format("未到打卡时间（可在预约开始前 %d 分钟内打卡）", CHECKIN_ADVANCE_MINUTES));
        }
        if (now.isAfter(reservation.getStartTime().plusMinutes(CHECKIN_LATE_MINUTES))) {
            throw BizException.badRequest(String.format("已超过打卡时限（需在预约开始后 %d 分钟内完成打卡）", CHECKIN_LATE_MINUTES));
        }
        reservation.setCheckinFlag(1);
        reservation.setCheckinTime(now);
        reservation.setStatus(Reservation.STATUS_IN_USE);
        reservationMapper.updateById(reservation);
        fillResources(List.of(reservation));
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_CHECKIN, "完成打卡（方式：验证码）");
        return reservation;
    }

    /** 归属校验：本人或管理员 */
    private Reservation getOwned(Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw BizException.notFound("预约不存在");
        }
        UserContext.LoginUser ctx = UserContext.get();
        boolean admin = ctx != null && ctx.getRole() != null && ctx.getRole() == 1;
        if (!admin && !reservation.getUserId().equals(ctx == null ? null : ctx.getUserId())) {
            throw BizException.forbidden("无权操作他人预约");
        }
        return reservation;
    }

    private void fillResources(List<Reservation> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(Reservation::getResourceId).distinct().toList();
        Map<Long, Resource> map = resourceMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Resource::getId, Function.identity()));
        for (Reservation r : records) {
            r.setResource(map.get(r.getResourceId()));
        }
    }

    /** 管理端：全部预约分页（含用户名与资源信息，可按用户名/状态/资源类型筛选） */
    public Page<Reservation> adminPage(Integer current, Integer size, String username, Integer status, Integer resourceType) {
        refreshStatuses();
        int cur = current == null || current < 1 ? 1 : current;
        int sz = size == null || size < 1 ? 10 : size;
        var qw = Wrappers.<Reservation>lambdaQuery()
                .eq(status != null, Reservation::getStatus, status)
                .orderByDesc(Reservation::getCreatedAt);
        if (StringUtils.hasText(username)) {
            List<Long> uids = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                            .like(SysUser::getUsername, username)).stream()
                    .map(SysUser::getId).toList();
            if (uids.isEmpty()) {
                return new Page<>(cur, sz);
            }
            qw.in(Reservation::getUserId, uids);
        }
        if (resourceType != null) {
            List<Long> rids = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                            .eq(Resource::getResourceType, resourceType)).stream()
                    .map(Resource::getId).toList();
            if (rids.isEmpty()) {
                return new Page<>(cur, sz);
            }
            qw.in(Reservation::getResourceId, rids);
        }
        Page<Reservation> page = reservationMapper.selectPage(new Page<>(cur, sz), qw);
        fillResources(page.getRecords());
        fillUsers(page.getRecords());
        fillCheckinCodes(page.getRecords());
        return page;
    }

    /** 回填关联用户（管理端展示用户名） */
    private void fillUsers(List<Reservation> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(Reservation::getUserId).distinct().toList();
        Map<Long, SysUser> map = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        for (Reservation r : records) {
            r.setUser(map.get(r.getUserId()));
        }
    }

    /**
     * 回填待打卡预约对应资源的当日验证码（管理端展示，便于复制告知用户）。
     * 仅对待打卡预约生效，且会按需生成当日验证码。
     */
    private void fillCheckinCodes(List<Reservation> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Reservation r : records) {
            if (r.getStatus() != null && r.getStatus() == Reservation.STATUS_PENDING && r.getResourceId() != null) {
                r.setCheckinCode(checkinCodeService.ensureTodayCode(r.getResourceId()).getCode());
            }
        }
    }

    /** 写一条预约操作日志（自动补全资源名） */
    private void writeLog(Long userId, Long reservationId, Long resourceId, int action, String detail) {
        String resourceName = null;
        if (resourceId != null) {
            Resource res = resourceMapper.selectById(resourceId);
            if (res != null) {
                resourceName = res.getName();
            }
        }
        logService.record(userId, reservationId, resourceId, resourceName, action, detail);
    }

    /** 登记违规并扣减信用分（下限 0） */
    private void recordViolation(Long userId, Long reservationId, int type, String description, int deductScore) {
        Violation violation = new Violation();
        violation.setUserId(userId);
        violation.setReservationId(reservationId);
        violation.setType(type);
        violation.setDescription(description);
        violation.setDeductScore(deductScore);
        violation.setStatus(1);
        violationMapper.insert(violation);

        SysUser user = userMapper.selectById(userId);
        if (user != null) {
            SysUser update = new SysUser();
            update.setId(userId);
            update.setCreditScore(Math.max(0, (user.getCreditScore() == null ? 100 : user.getCreditScore()) - deductScore));
            userMapper.updateById(update);
        }
    }
}

package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ReservationDTO;
import com.campus.reservation.dto.ReservationTraceVO;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    /** 一日内违规次数达到该值，当日禁止预约（次日自动恢复） */
    public static final int DAILY_VIOLATION_LIMIT = 3;
    /** 可提前打卡的分钟数（预约开始前） */
    public static final int CHECKIN_ADVANCE_MINUTES = 30;
    /** 开始后仍可打卡的分钟数，超过则自动爽约并释放空间 */
    public static final int CHECKIN_LATE_MINUTES = 5;
    /** 单次预约最短时长（分钟）：过短的碎片时段既无实际使用价值，也让资源难以周转 */
    public static final int MIN_DURATION_MINUTES = 30;
    /** 累计取消预约次数上限：达到该值记恶意取消，次数存 sys_user.cancel_count，管理端可修正 */
    public static final int CANCEL_LIMIT = 5;
    /** 实验室(2)/体育馆(4)：仅可预约未来一周内的时间（含第 7 天） */
    public static final int LAB_GYM_ADVANCE_DAYS = 7;
    /** 图书馆(3)：最多只能预约到次日 */
    public static final int LIBRARY_ADVANCE_DAYS = 1;

    /** 操作日志中时间的展示格式 */
    private static final DateTimeFormatter LOG_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 预约状态文案（下标即状态值），用于管理端操作留痕 */
    private static final String[] STATUS_LABELS = {"未知", "待打卡", "使用中", "已结束", "已取消", "爽约", "违规"};

    private final ReservationMapper reservationMapper;
    private final ResourceMapper resourceMapper;
    private final SysUserMapper userMapper;
    private final HolidayMapper holidayMapper;
    private final ViolationMapper violationMapper;
    private final ReservationLogService logService;
    private final SystemConfigService systemConfigService;
    private final ClosureService closureService;

    /** 当前登录者是否为管理员（role=1） */
    private static boolean isAdmin() {
        UserContext.LoginUser ctx = UserContext.get();
        return ctx != null && ctx.getRole() != null && ctx.getRole() == 1;
    }

    /**
     * 创建预约：规则校验（当日违规次数/资源状态/节假日/开放时间/最短 30 分钟，时长不设上限）+ 时间冲突检测。
     * 冲突判定采用半开区间 [start, end)：newStart < existEnd AND newEnd > existStart。
     */
    @Transactional
    public Reservation create(ReservationDTO dto) {
        Long userId = UserContext.requireUserId();
        if (dto.getResourceId() == null || dto.getStartTime() == null || dto.getEndTime() == null) {
            throw BizException.badRequest("缺少必要参数");
        }
        // 并发安全：事务内第一条查询即锁住资源行，同一资源的并发请求在此串行化。
        // 必须前置：若晚于普通 SELECT，RR 隔离级别下读快照已建立，拿锁后仍会漏读对手刚提交的预约
        resourceMapper.lockForReservation(dto.getResourceId());
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("账号不可用");
        }
        // 一键闭馆：闭馆期间禁止普通用户预约，管理员不受限
        if (!isAdmin() && systemConfigService.isClosed()) {
            throw BizException.forbidden("校园当前处于闭馆状态，暂停开放预约，恢复时间以平台通知为准");
        }
        // 预约权限：管理员可强制受限/强制正常，未设置时按当日违规次数自动判定
        if (!isAdmin()) {
            Integer override = user.getRestrictOverride();
            if (override != null && override == 2) {
                throw BizException.forbidden(
                        "您的账号预约权限当前处于受限状态，暂无法新建预约。如需恢复，请通过「意见反馈」提交申诉，工作人员将尽快核实处理");
            }
            if (override == null || override == 0) {
                Long todayViolations = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                        .eq(Violation::getUserId, userId)
                        .eq(Violation::getStatus, 1)
                        .ge(Violation::getCreatedAt, LocalDate.now().atStartOfDay()));
                if (todayViolations != null && todayViolations >= DAILY_VIOLATION_LIMIT) {
                    throw BizException.forbidden(String.format(
                            "您今日已有 %d 次违规记录，按平台规则今日暂停预约，次日自动恢复", todayViolations));
                }
            }
        }
        Resource resource = resourceMapper.selectById(dto.getResourceId());
        if (resource == null) {
            throw BizException.notFound("资源不存在");
        }
        if (resource.getStatus() != null && resource.getStatus() == Resource.STATUS_REPAIRING) {
            throw BizException.conflict("该资源正在维修中，暂不可预约");
        }
        if (resource.getStatus() != null && resource.getStatus() == Resource.STATUS_REPORTED) {
            throw BizException.conflict("该资源已报修，等待处理，暂不可预约");
        }
        if (resource.getStatus() != null && resource.getStatus() == Resource.STATUS_IN_USE) {
            throw BizException.conflict("该资源正在被使用");
        }
        if (resource.getResourceType() != null && resource.getResourceType() == 1) {
            throw BizException.badRequest("教室无需预约，未被课程占用时可直接使用");
        }

        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = dto.getEndTime();
        // 定向闭馆：命中整类或该具体资源的闭馆时段则禁止预约（管理员不受限，与总体闭馆一致）
        if (!isAdmin() && closureService.isClosedAt(resource.getId(), resource.getResourceType(), start, end)) {
            throw BizException.conflict("该资源在此时段已闭馆，暂不可预约");
        }
        if (!start.isBefore(end)) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        if (Duration.between(start, end).toMinutes() < MIN_DURATION_MINUTES) {
            throw BizException.badRequest("单次预约时长不能少于 " + MIN_DURATION_MINUTES + " 分钟");
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

        // 按资源类型限制可提前预约的日期范围（管理员不受限）：
        // 实验室(2)/体育馆(4)仅可预约未来一周内；图书馆(3)最多只能预约到次日
        if (!isAdmin()) {
            Integer rtype = resource.getResourceType();
            LocalDate resToday = LocalDate.now();
            LocalDate resStartDate = start.toLocalDate();
            if (rtype != null && (rtype == 2 || rtype == 4)
                    && resStartDate.isAfter(resToday.plusDays(LAB_GYM_ADVANCE_DAYS))) {
                throw BizException.badRequest("实验室、体育馆仅可预约未来一周内的时间");
            }
            if (rtype != null && rtype == 3
                    && resStartDate.isAfter(resToday.plusDays(LIBRARY_ADVANCE_DAYS))) {
                throw BizException.badRequest("图书馆最多只能预约到次日");
            }
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
            throw BizException.conflict(String.format("您在该时段已有预约：%s %s ~ %s，同一时段仅可预约一个资源",
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
        // 冲突查询用锁定读（当前读），不受事务早期快照影响，确保能看到已提交的并发预约
        List<Reservation> overlapping = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getResourceId, dto.getResourceId())
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .lt(Reservation::getStartTime, end)
                .gt(Reservation::getEndTime, start)
                .last("FOR UPDATE"));
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
        // 累计预约次数 +1（管理端可修正，与预约记录列表解耦）
        SysUser counter = new SysUser();
        counter.setId(userId);
        counter.setReservationTotal((user.getReservationTotal() == null ? 0 : user.getReservationTotal()) + 1);
        userMapper.updateById(counter);
        writeLog(userId, reservation.getId(), dto.getResourceId(), ReservationLog.ACTION_CREATE,
                String.format("创建预约：%s %s ~ %s", resource.getName(), start.format(LOG_TIME_FMT), end.format(LOG_TIME_FMT)));
        return reservation;
    }

    /**
     * 懒惰状态流转：
     * 1) 超过打卡时限（开始后 5 分钟）仍未打卡 → 爽约（记违规扣信用分）并释放空间；
     * 2) 到期未结束 → 已完成。
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
                    "预约开始后未在规定时间内完成打卡，系统已记录");
            writeLogSystem(r.getUserId(), r.getId(), r.getResourceId(), ReservationLog.ACTION_NOSHOW,
                    String.format("超过打卡时限（开始后 %d 分钟内未打卡），系统自动释放空间并记为爽约", CHECKIN_LATE_MINUTES));
        }

        List<Reservation> finished = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Reservation.STATUS_IN_USE)
                .lt(Reservation::getEndTime, now));
        for (Reservation r : finished) {
            r.setStatus(Reservation.STATUS_COMPLETED);
            reservationMapper.updateById(r);
            writeLogSystem(r.getUserId(), r.getId(), r.getResourceId(), ReservationLog.ACTION_COMPLETE,
                    "预约到期，系统自动标记为已完成");
        }
    }

    /** 提前释放：使用中的预约主动结束，立即释放空间 */
    @Transactional
    public Reservation release(Long id) {
        Reservation reservation = getOwned(id);
        if (reservation.getStatus() != Reservation.STATUS_IN_USE) {
            throw BizException.badRequest("仅使用中的预约可提前释放");
        }
        reservation.setStatus(Reservation.STATUS_COMPLETED);
        reservationMapper.updateById(reservation);
        fillResources(List.of(reservation));
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_RELEASE, "提前释放空间，预约结束");
        return reservation;
    }

    /**
     * 我的预约：可按状态、按预约开始日期（单日）筛选。
     * date 只比对 start_time 所在的自然日，即"这天的预约"，不跨天拆分。
     */
    public Page<Reservation> my(Integer current, Integer size, Integer status, LocalDate date) {
        refreshStatuses();
        LocalDateTime dayStart = date == null ? null : date.atStartOfDay();
        LocalDateTime dayEnd = date == null ? null : date.plusDays(1).atStartOfDay();
        Page<Reservation> page = reservationMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getUserId, UserContext.requireUserId())
                        .eq(status != null, Reservation::getStatus, status)
                        .ge(dayStart != null, Reservation::getStartTime, dayStart)
                        .lt(dayEnd != null, Reservation::getStartTime, dayEnd)
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

    /**
     * 单条预约的「当时情况」：预约本体 + 操作时间线 + 关联违规。
     * 用户端预约详情、违规记录详情侧拉框使用；归属校验与详情一致（本人或管理员），
     * 违规只返回对该预约产生的记录，含已撤销的（让用户看得到“先记后被撤销”的完整过程）。
     */
    public ReservationTraceVO trace(Long id) {
        refreshStatuses();
        Reservation reservation = getOwned(id);
        fillResources(List.of(reservation));
        List<ReservationLog> logs = logService.listByReservation(id);
        List<Violation> violations = violationMapper.selectList(Wrappers.<Violation>lambdaQuery()
                .eq(Violation::getReservationId, id)
                .orderByAsc(Violation::getCreatedAt)
                .orderByAsc(Violation::getId));
        return new ReservationTraceVO(reservation, logs, violations);
    }

    @Transactional
    public Reservation cancel(Long id) {
        Reservation reservation = getOwned(id);
        if (reservation.getStatus() != Reservation.STATUS_PENDING) {
            throw BizException.badRequest("当前状态下不可取消预约");
        }
        SysUser user = userMapper.selectById(reservation.getUserId());
        int before = user == null || user.getCancelCount() == null ? 0 : user.getCancelCount();
        // 累计取消已达上限（已记恶意取消）：用户不可再自行取消，只能等管理员重置次数后恢复。
        // 管理员代为处理不受限，否则用户的待打卡预约将无处可退。
        if (!isAdmin() && before >= CANCEL_LIMIT) {
            throw BizException.forbidden(String.format(
                    "您的预约取消次数已达上限（%d 次），暂不可继续取消预约。如需帮助，请通过「意见反馈」提交申诉",
                    CANCEL_LIMIT));
        }
        reservation.setStatus(Reservation.STATUS_CANCELLED);
        reservationMapper.updateById(reservation);
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_CANCEL,
                String.format("取消预约：%s ~ %s",
                        reservation.getStartTime().format(LOG_TIME_FMT), reservation.getEndTime().format(LOG_TIME_FMT)));

        // 累计取消次数 +1 并封顶在 CANCEL_LIMIT：上限即上限，不允许再出现 6/5 这类越界计数
        int cancelCount = Math.min(before + 1, CANCEL_LIMIT);
        if (user != null) {
            SysUser patch = new SysUser();
            patch.setId(user.getId());
            patch.setCancelCount(cancelCount);
            userMapper.updateById(patch);
        }
        // 达到上限时记一次恶意取消；同一用户已有有效记录则不重复记（管理员撤销后可再记）
        if (before + 1 >= CANCEL_LIMIT) {
            Long exists = violationMapper.selectCount(Wrappers.<Violation>lambdaQuery()
                    .eq(Violation::getUserId, reservation.getUserId())
                    .eq(Violation::getType, Violation.TYPE_BAD_CANCEL)
                    .eq(Violation::getStatus, 1));
            if (exists == null || exists == 0) {
                recordViolation(reservation.getUserId(), id, Violation.TYPE_BAD_CANCEL,
                        "预约取消次数累计达 " + CANCEL_LIMIT + " 次上限，系统已记录");
            }
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
        return checkin(id, Reservation.CHECKIN_TYPE_CODE, "完成验证码打卡");
    }

    /** 完成打卡并记录打卡方式（验证码打卡，无定位数据） */
    @Transactional
    public Reservation checkin(Long id, int checkinType, String checkinDetail) {
        return checkin(id, checkinType, checkinDetail, null, null, null, null);
    }

    /**
     * 完成打卡并记录打卡方式。
     * 定位打卡时同时落库用户坐标、距离与定位精度，供管理端在预约详情里追溯。
     */
    @Transactional
    public Reservation checkin(Long id, int checkinType, String checkinDetail,
                               BigDecimal latitude, BigDecimal longitude,
                               Integer distance, Integer accuracy) {
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
        reservation.setCheckinType(checkinType);
        reservation.setCheckinTime(now);
        reservation.setCheckinLatitude(latitude);
        reservation.setCheckinLongitude(longitude);
        reservation.setCheckinDistance(distance);
        reservation.setCheckinAccuracy(accuracy);
        reservation.setStatus(Reservation.STATUS_IN_USE);
        reservationMapper.updateById(reservation);
        fillResources(List.of(reservation));
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_CHECKIN, checkinDetail);
        return reservation;
    }

    /**
     * 管理端：直接修改预约状态（与用户端同一套状态值）。
     * 切到使用中时补打卡标记（不受现场打卡时间窗口限制，视为管理员代为打卡）。
     */
    @Transactional
    public Reservation adminUpdateStatus(Long id, Integer status) {
        UserContext.requireAdmin();
        if (status == null || status < Reservation.STATUS_PENDING || status > Reservation.STATUS_VIOLATED) {
            throw BizException.badRequest("预约状态不合法");
        }
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw BizException.notFound("预约不存在");
        }
        int before = reservation.getStatus() == null ? 0 : reservation.getStatus();
        if (before == status) {
            throw BizException.badRequest("该预约已经是「" + statusLabel(status) + "」，无需修改");
        }
        reservation.setStatus(status);
        if (status == Reservation.STATUS_IN_USE) {
            if (reservation.getCheckinFlag() == null || reservation.getCheckinFlag() != 1) {
                reservation.setCheckinFlag(1);
                reservation.setCheckinType(Reservation.CHECKIN_TYPE_ADMIN);
                reservation.setCheckinTime(LocalDateTime.now());
            }
        }
        reservationMapper.updateById(reservation);
        fillResources(List.of(reservation));
        String resourceName = reservation.getResource() == null ? "该资源" : reservation.getResource().getName();
        writeLog(reservation.getUserId(), reservation.getId(), reservation.getResourceId(),
                ReservationLog.ACTION_ADMIN_UPDATE,
                String.format("管理员调整预约状态：%s → %s（%s，%s ~ %s）",
                        statusLabel(before), statusLabel(status), resourceName,
                        reservation.getStartTime().format(LOG_TIME_FMT),
                        reservation.getEndTime().format(LOG_TIME_FMT)));
        return reservation;
    }

    private static String statusLabel(Integer status) {
        return status == null || status < 0 || status >= STATUS_LABELS.length
                ? STATUS_LABELS[0] : STATUS_LABELS[status];
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

    /** 管理端：全部预约分页（仅普通用户的预约，可按用户名/状态/资源类型/校区/资源/日期筛选） */
    public Page<Reservation> adminPage(Integer current, Integer size, String username, Integer status, Integer resourceType,
                                       Long resourceId, LocalDate date, String campus,
                                       LocalDate startDate, LocalDate endDate) {
        refreshStatuses();
        int cur = current == null || current < 1 ? 1 : current;
        int sz = size == null || size < 1 ? 10 : size;
        // 管理员自己的预约不进「预约管理」：这里只管理普通用户的预约
        List<Long> normalUserIds = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getRole, 0)).stream()
                .map(SysUser::getId).toList();
        if (normalUserIds.isEmpty()) {
            return new Page<>(cur, sz);
        }
        var qw = Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getUserId, normalUserIds)
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
        // 按校区过滤：先取该校区全部资源，再限定预约
        if (StringUtils.hasText(campus)) {
            List<Long> cids = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                            .eq(Resource::getCampus, campus)).stream()
                    .map(Resource::getId).toList();
            if (cids.isEmpty()) {
                return new Page<>(cur, sz);
            }
            qw.in(Reservation::getResourceId, cids);
        }
        // 按具体资源过滤（资源档案抽屉里展示该资源的预约占用）
        if (resourceId != null) {
            qw.eq(Reservation::getResourceId, resourceId);
        }
        // 按日期过滤：与当天有交集的预约（半开区间）
        if (date != null) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            qw.lt(Reservation::getStartTime, dayEnd).gt(Reservation::getEndTime, dayStart);
        }
        // 按日期区间过滤：与 [startDate, endDate] 任意一天有交集即命中
        if (startDate != null || endDate != null) {
            LocalDate from = startDate != null ? startDate : endDate;
            LocalDate to = endDate != null ? endDate : startDate;
            qw.lt(Reservation::getStartTime, to.plusDays(1).atStartOfDay())
                    .gt(Reservation::getEndTime, from.atStartOfDay());
        }
        Page<Reservation> page = reservationMapper.selectPage(new Page<>(cur, sz), qw);
        fillResources(page.getRecords());
        fillUsers(page.getRecords());
        return page;
    }

    /** 管理端：单条预约详情（含用户、资源与打卡追溯信息），不受归属关系限制 */
    public Reservation adminDetail(Long id) {
        UserContext.requireAdmin();
        refreshStatuses();
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw BizException.notFound("预约不存在");
        }
        fillResources(List.of(reservation));
        fillUsers(List.of(reservation));
        return reservation;
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

    /** 写一条预约操作日志（自动补全资源名） */
    private void writeLog(Long userId, Long reservationId, Long resourceId, int action, String detail) {
        logService.record(userId, reservationId, resourceId, resourceNameOf(resourceId), action, detail);
    }

    /** 写一条系统自动产生的日志（定时爽约/到期完成），操作人记为「系统自动」 */
    private void writeLogSystem(Long userId, Long reservationId, Long resourceId, int action, String detail) {
        logService.recordSystem(userId, reservationId, resourceId, resourceNameOf(resourceId), action, detail);
    }

    private String resourceNameOf(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        Resource res = resourceMapper.selectById(resourceId);
        return res == null ? null : res.getName();
    }

    /** 记录一次违规：仅落库供管理端查阅，不再使用信用分计法 */
    private void recordViolation(Long userId, Long reservationId, int type, String description) {
        Violation violation = new Violation();
        violation.setUserId(userId);
        violation.setReservationId(reservationId);
        violation.setType(type);
        violation.setDescription(description);
        violation.setDeductScore(0);
        violation.setStatus(1);
        violationMapper.insert(violation);
    }
}

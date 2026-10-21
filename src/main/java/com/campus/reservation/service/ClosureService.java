package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ClosureDTO;
import com.campus.reservation.entity.CampusClosure;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.ReservationLog;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.CampusClosureMapper;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 按资源类型的定时闭馆：实验室(2)/图书馆(3)/体育馆(4)在具体日期、具体时段的闭馆管理。
 * 总体「一键闭馆」开关仍由 {@link SystemConfigService} 负责，本服务不影响它。
 */
@Service
@RequiredArgsConstructor
public class ClosureService {

    /** 可闭馆的资源类型：2 实验室、3 图书馆、4 体育馆（教室不参与预约，不可闭馆） */
    private static final List<Integer> CLOSABLE_TYPES = List.of(2, 3, 4);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CampusClosureMapper closureMapper;
    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationLogService logService;
    private final MessageService messageService;

    /** 查询全部生效中的闭馆窗口（按日期、时刻升序） */
    public List<CampusClosure> listActive() {
        return closureMapper.selectList(Wrappers.<CampusClosure>lambdaQuery()
                .eq(CampusClosure::getEnabled, CampusClosure.ENABLED)
                .orderByAsc(CampusClosure::getClosureDate)
                .orderByAsc(CampusClosure::getStartTime));
    }

    /**
     * 判断某类型资源在 [start, end) 时段是否与任一生效闭馆窗口重叠。
     * 半开区间重叠：rStart &lt; cEnd AND rEnd &gt; cStart。
     */
    public boolean isClosedAt(Integer resourceType, LocalDateTime start, LocalDateTime end) {
        if (resourceType == null || start == null || end == null) {
            return false;
        }
        for (CampusClosure c : listActive()) {
            if (!resourceType.equals(c.getResourceType())) {
                continue;
            }
            LocalDateTime cs = LocalDateTime.of(c.getClosureDate(), c.getStartTime());
            LocalDateTime ce = LocalDateTime.of(c.getClosureDate(), c.getEndTime());
            if (start.isBefore(ce) && end.isAfter(cs)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建类型闭馆：
     * 1) 管理员校验 + 参数校验；
     * 2) 占用拦截：闭馆窗口覆盖当前时刻且该类型存在使用中资源时禁止闭馆；
     * 3) 落库闭馆窗口；
     * 4) 自动取消落在闭馆时段内的未来预约并向预约者发送通知。
     * 返回被自动取消的预约数量。
     */
    @Transactional
    public int create(ClosureDTO dto) {
        UserContext.requireAdmin();
        if (dto.getResourceType() == null || !CLOSABLE_TYPES.contains(dto.getResourceType())) {
            throw BizException.badRequest("仅支持对实验室/图书馆/体育馆设置闭馆");
        }
        if (dto.getClosureDate() == null || dto.getStartTime() == null || dto.getEndTime() == null) {
            throw BizException.badRequest("闭馆日期与起止时刻不能为空");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw BizException.badRequest("闭馆结束时刻必须晚于开始时刻");
        }

        LocalDateTime windowStart = LocalDateTime.of(dto.getClosureDate(), dto.getStartTime());
        LocalDateTime windowEnd = LocalDateTime.of(dto.getClosureDate(), dto.getEndTime());
        LocalDateTime now = LocalDateTime.now();

        List<Long> resourceIds = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                        .eq(Resource::getResourceType, dto.getResourceType())).stream()
                .map(Resource::getId)
                .toList();

        // 占用拦截：闭馆窗口覆盖当前时刻且该类型此刻有资源正被使用（status=IN_USE）
        boolean coversNow = !windowStart.isAfter(now) && windowEnd.isAfter(now);
        if (coversNow && !resourceIds.isEmpty()) {
            Long inUse = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                    .in(Reservation::getResourceId, resourceIds)
                    .eq(Reservation::getStatus, Reservation.STATUS_IN_USE)
                    .le(Reservation::getStartTime, now)
                    .gt(Reservation::getEndTime, now));
            if (inUse != null && inUse > 0) {
                throw BizException.conflict("该类型有资源正在使用中，无法闭馆，请待使用结束后再操作");
            }
        }

        CampusClosure closure = new CampusClosure();
        closure.setResourceType(dto.getResourceType());
        closure.setClosureDate(dto.getClosureDate());
        closure.setStartTime(dto.getStartTime());
        closure.setEndTime(dto.getEndTime());
        closure.setReason(dto.getReason());
        closure.setEnabled(CampusClosure.ENABLED);
        closureMapper.insert(closure);

        // 自动取消受影响的未来预约并通知
        int cancelled = cancelAffectedReservations(closure, resourceIds, windowStart, windowEnd, now);

        // 向全体用户群发闭馆通知（受影响预约的逐人取消通知已在上面发送）
        messageService.broadcast(String.format(
                "【闭馆通知】%s 将于 %s %s~%s 闭馆（原因：%s），期间该类型资源暂停预约，%d 条受影响预约已自动取消并单独通知。给您带来不便，敬请谅解。",
                typeLabel(dto.getResourceType()), dto.getClosureDate(), dto.getStartTime(), dto.getEndTime(),
                (dto.getReason() == null || dto.getReason().isBlank()) ? "场馆管理需要" : dto.getReason(),
                cancelled), MessageService.SCOPE_ALL);
        return cancelled;
    }

    /** 撤销闭馆窗口（置为不生效） */
    @Transactional
    public void revoke(Long id) {
        UserContext.requireAdmin();
        CampusClosure closure = closureMapper.selectById(id);
        if (closure == null) {
            throw BizException.notFound("闭馆记录不存在");
        }
        closure.setEnabled(CampusClosure.DISABLED);
        closureMapper.updateById(closure);
    }

    /**
     * 取消与该闭馆窗口时段重叠、且尚未结束的进行中/待打卡预约：
     * 系统发起取消（记操作日志、不记违规），并向每位预约者推送通知。返回取消数量。
     */
    private int cancelAffectedReservations(CampusClosure closure, List<Long> resourceIds,
                                           LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime now) {
        if (resourceIds.isEmpty()) {
            return 0;
        }
        List<Reservation> affected = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getResourceId, resourceIds)
                .in(Reservation::getStatus, List.of(Reservation.STATUS_PENDING, Reservation.STATUS_IN_USE))
                .ge(Reservation::getEndTime, now)
                .lt(Reservation::getStartTime, windowEnd)
                .gt(Reservation::getEndTime, windowStart));
        String typeName = typeLabel(closure.getResourceType());
        String windowText = String.format("%s %s ~ %s",
                closure.getClosureDate().format(DATE_FMT), closure.getStartTime(), closure.getEndTime());
        String reason = (closure.getReason() == null || closure.getReason().isBlank())
                ? "场馆管理需要" : closure.getReason();
        for (Reservation r : affected) {
            r.setStatus(Reservation.STATUS_CANCELLED);
            reservationMapper.updateById(r);
            Resource res = resourceMapper.selectById(r.getResourceId());
            String resourceName = res == null ? typeName + "资源" : res.getName();
            logService.record(r.getUserId(), r.getId(), r.getResourceId(), resourceName,
                    ReservationLog.ACTION_CANCEL,
                    String.format("管理员闭馆（%s %s ~ %s）自动取消预约：%s ~ %s",
                            typeName, windowText, reason,
                            r.getStartTime().format(TIME_FMT), r.getEndTime().format(TIME_FMT)));
            messageService.notifyUser(r.getUserId(), String.format(
                    "因%s于 %s 闭馆（原因：%s），您在「%s」%s ~ %s 的预约已被自动取消，请重新安排，如有疑问请联系管理员。",
                    typeName, closure.getStartTime() + "-" + closure.getEndTime() + " " + closure.getClosureDate().format(DATE_FMT),
                    reason, resourceName,
                    r.getStartTime().format(TIME_FMT), r.getEndTime().format(TIME_FMT)));
        }
        return affected.size();
    }

    private static String typeLabel(Integer type) {
        if (type == null) return "资源";
        return switch (type) {
            case 2 -> "实验室";
            case 3 -> "图书馆";
            case 4 -> "体育馆";
            default -> "资源";
        };
    }
}

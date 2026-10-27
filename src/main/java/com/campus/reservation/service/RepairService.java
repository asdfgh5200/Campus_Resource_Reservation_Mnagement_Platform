package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.RepairDTO;
import com.campus.reservation.dto.RepairHandleDTO;
import com.campus.reservation.entity.RepairOrder;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.RepairOrderMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairService {

    /** 用户端「我的工单」最多返回的条数 */
    private static final int MY_ORDER_LIMIT = 100;

    /** 驳回原因长度上限（与 repair_order.reject_reason 一致） */
    private static final int REJECT_REASON_MAX = 200;

    /** 管理员驳回时未填原因时的默认说明，保证通知不出现空白 */
    private static final String DEFAULT_REJECT_REASON = "经核实暂不需要维修";

    private final RepairOrderMapper repairOrderMapper;
    private final ResourceMapper resourceMapper;
    private final SysUserMapper userMapper;
    private final MessageService messageService;

    /**
     * 用户提交维修申请：只生成「待审核」工单，不动资源状态（地点仍可正常预约）。
     * 管理员审核确认维修后才会置为「维修中」并暂停预约。
     * 提交即回一条「已受理」通知，让报修人在消息中心看到完整进度留痕。
     */
    public RepairOrder create(RepairDTO dto) {
        if (dto.getResourceId() == null || !StringUtils.hasText(dto.getProblem())) {
            throw BizException.badRequest("缺少必要参数");
        }
        Resource resource = resourceMapper.selectById(dto.getResourceId());
        if (resource == null) {
            throw BizException.notFound("资源不存在");
        }
        RepairOrder order = new RepairOrder();
        order.setResourceId(dto.getResourceId());
        order.setUserId(UserContext.requireUserId());
        order.setProblem(dto.getProblem());
        order.setImageUrl(dto.getImageUrl());
        order.setStatus(RepairOrder.STATUS_TODO);
        repairOrderMapper.insert(order);
        order.setResource(resource);
        messageService.notifyUser(order.getUserId(), String.format(
                "【维修申请已受理】你提交的「%s」维修申请（工单 #%d）已收到，工作人员审核后会通过站内通知告诉你处理结果。",
                resource.getName(), order.getId()), order.getResourceId());
        return order;
    }

    /** 我的维修工单：用户端在「消息中心 - 维修工单」里查看进度，附资源当前状态用于跳转核对 */
    public List<RepairOrder> myOrders() {
        Long userId = UserContext.requireUserId();
        List<RepairOrder> orders = repairOrderMapper.selectList(Wrappers.<RepairOrder>lambdaQuery()
                .eq(RepairOrder::getUserId, userId)
                .orderByDesc(RepairOrder::getCreatedAt)
                .last("LIMIT " + MY_ORDER_LIMIT));
        List<Long> ids = orders.stream().map(RepairOrder::getResourceId).distinct().toList();
        if (!ids.isEmpty()) {
            Map<Long, Resource> map = resourceMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Resource::getId, Function.identity()));
            orders.forEach(o -> o.setResource(map.get(o.getResourceId())));
        }
        return orders;
    }

    public Page<RepairOrder> page(Integer status, Integer current, Integer size) {
        Page<RepairOrder> page = repairOrderMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<RepairOrder>lambdaQuery()
                        .eq(status != null, RepairOrder::getStatus, status)
                        .orderByDesc(RepairOrder::getCreatedAt));
        List<Long> resourceIds = page.getRecords().stream().map(RepairOrder::getResourceId).distinct().toList();
        if (!resourceIds.isEmpty()) {
            Map<Long, Resource> map = resourceMapper.selectBatchIds(resourceIds).stream()
                    .collect(Collectors.toMap(Resource::getId, Function.identity()));
            page.getRecords().forEach(o -> o.setResource(map.get(o.getResourceId())));
        }
        fillReporters(page.getRecords());
        return page;
    }

    /** 管理端列表补报修人姓名/学号，方便管理员核实问题与回访（不存入工单表，随查随拼） */
    private void fillReporters(List<RepairOrder> records) {
        List<Long> userIds = records.stream().map(RepairOrder::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        records.forEach(o -> {
            SysUser u = users.get(o.getUserId());
            if (u == null) {
                return;
            }
            o.setReporterName(StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername());
            o.setReporterAccount(u.getUsername());
        });
    }

    /**
     * 管理员处理工单，与资源状态联动，并自动给报修人发一条进度通知：
     * 确认维修(1) → 资源置为维修中；维修完成(2) → 恢复可预约；
     * 驳回(3) → 该资源无其他未完成工单时，从报修中恢复为可预约。
     */
    @Transactional
    public RepairOrder handle(Long id, RepairHandleDTO dto) {
        if (dto.getStatus() == null || dto.getStatus() < 1 || dto.getStatus() > 3) {
            throw BizException.badRequest("处理状态不合法");
        }
        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("工单不存在");
        }
        if (order.getStatus() == RepairOrder.STATUS_DONE || order.getStatus() == RepairOrder.STATUS_REJECTED) {
            throw BizException.badRequest("该工单已处理完成");
        }
        String rejectReason = null;
        if (dto.getStatus() == RepairOrder.STATUS_REJECTED) {
            rejectReason = StringUtils.hasText(dto.getReason()) ? dto.getReason().trim() : DEFAULT_REJECT_REASON;
            if (rejectReason.length() > REJECT_REASON_MAX) {
                throw BizException.badRequest("驳回原因不能超过 " + REJECT_REASON_MAX + " 字");
            }
            order.setRejectReason(rejectReason);
        }
        order.setStatus(dto.getStatus());
        order.setHandleUser(UserContext.requireUserId());
        order.setHandleTime(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        if (dto.getStatus() == RepairOrder.STATUS_PROCESSING) {
            patchResourceStatus(order.getResourceId(), Resource.STATUS_REPAIRING);
        } else if (dto.getStatus() == RepairOrder.STATUS_DONE) {
            patchResourceStatus(order.getResourceId(), Resource.STATUS_AVAILABLE);
        } else {
            restoreIfNoPendingOrder(order);
        }
        notifyProgress(order, rejectReason);
        return order;
    }

    /**
     * 把处理结果推给报修人：通知带上 resource_id，用户端「通知」可一键跳到该地点。
     * 文案与管理端状态一一对应，避免用户只看到一个孤零零的状态值。
     */
    private void notifyProgress(RepairOrder order, String rejectReason) {
        Resource resource = resourceMapper.selectById(order.getResourceId());
        String name = resource == null ? "该地点" : resource.getName();
        String content = switch (order.getStatus()) {
            case RepairOrder.STATUS_PROCESSING -> String.format(
                    "【维修中】你报修的「%s」（工单 #%d）已通过审核，地点已标为「维修中」并暂停预约，维修完成后会再次通知你。",
                    name, order.getId());
            case RepairOrder.STATUS_DONE -> String.format(
                    "【维修完成】你报修的「%s」（工单 #%d）已维修完成，地点已恢复「可预约」，感谢你的反馈。",
                    name, order.getId());
            case RepairOrder.STATUS_REJECTED -> String.format(
                    "【工单已驳回】你报修的「%s」（工单 #%d）未进入维修流程，原因：%s。如仍有问题，可重新提交报修，或在「消息中心 - 沟通」联系我们。",
                    name, order.getId(), rejectReason);
            default -> null;
        };
        if (content != null) {
            messageService.notifyUser(order.getUserId(), content, order.getResourceId());
        }
    }

    /** 驳回：只有该资源已没有待处理/处理中的工单，才把「报修中」恢复为可预约 */
    private void restoreIfNoPendingOrder(RepairOrder order) {
        Resource resource = resourceMapper.selectById(order.getResourceId());
        if (resource == null || resource.getStatus() == null
                || resource.getStatus() != Resource.STATUS_REPORTED) {
            return;
        }
        Long pending = repairOrderMapper.selectCount(Wrappers.<RepairOrder>lambdaQuery()
                .eq(RepairOrder::getResourceId, order.getResourceId())
                .in(RepairOrder::getStatus, RepairOrder.STATUS_TODO, RepairOrder.STATUS_PROCESSING));
        if (pending == null || pending == 0) {
            patchResourceStatus(order.getResourceId(), Resource.STATUS_AVAILABLE);
        }
    }

    /** 只改状态，避免整行回写覆盖管理员刚编辑过的资源信息 */
    private void patchResourceStatus(Long resourceId, int status) {
        Resource patch = new Resource();
        patch.setId(resourceId);
        patch.setStatus(status);
        resourceMapper.updateById(patch);
    }
}

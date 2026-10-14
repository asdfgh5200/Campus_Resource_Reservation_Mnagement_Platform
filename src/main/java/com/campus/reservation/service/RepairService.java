package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.RepairDTO;
import com.campus.reservation.dto.RepairHandleDTO;
import com.campus.reservation.entity.RepairOrder;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.mapper.RepairOrderMapper;
import com.campus.reservation.mapper.ResourceMapper;
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

    private final RepairOrderMapper repairOrderMapper;
    private final ResourceMapper resourceMapper;

    /** 用户提交维修申请 */
    public RepairOrder create(RepairDTO dto) {
        if (dto.getResourceId() == null || !StringUtils.hasText(dto.getProblem())) {
            throw BizException.badRequest("缺少必要参数（resourceId/problem）");
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
        return order;
    }

    public Page<RepairOrder> page(Integer status, Integer current, Integer size) {
        Page<RepairOrder> page = repairOrderMapper.selectPage(
                new Page<>(current == null || current < 1 ? 1 : current, size == null || size < 1 ? 10 : size),
                Wrappers.<RepairOrder>lambdaQuery()
                        .eq(status != null, RepairOrder::getStatus, status)
                        .orderByDesc(RepairOrder::getCreatedAt));
        List<Long> ids = page.getRecords().stream().map(RepairOrder::getResourceId).distinct().toList();
        if (!ids.isEmpty()) {
            Map<Long, Resource> map = resourceMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Resource::getId, Function.identity()));
            page.getRecords().forEach(o -> o.setResource(map.get(o.getResourceId())));
        }
        return page;
    }

    /**
     * 管理员处理工单：
     * 确认维修(1) → 资源置为维修中；维修完成(2) → 恢复可预约；驳回(3) → 资源状态不变
     */
    @Transactional
    public RepairOrder handle(Long id, RepairHandleDTO dto) {
        if (dto.getStatus() == null || dto.getStatus() < 1 || dto.getStatus() > 3) {
            throw BizException.badRequest("处理状态不合法（1 处理中 / 2 已完成 / 3 已驳回）");
        }
        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("工单不存在");
        }
        if (order.getStatus() == RepairOrder.STATUS_DONE || order.getStatus() == RepairOrder.STATUS_REJECTED) {
            throw BizException.badRequest("该工单已处理完成");
        }
        order.setStatus(dto.getStatus());
        order.setHandleUser(UserContext.requireUserId());
        order.setHandleTime(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        if (dto.getStatus() == RepairOrder.STATUS_PROCESSING || dto.getStatus() == RepairOrder.STATUS_DONE) {
            Resource resource = new Resource();
            resource.setId(order.getResourceId());
            resource.setStatus(dto.getStatus() == RepairOrder.STATUS_PROCESSING ? 3 : 1);
            resourceMapper.updateById(resource);
        }
        return order;
    }
}

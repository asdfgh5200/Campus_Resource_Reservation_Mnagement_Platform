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

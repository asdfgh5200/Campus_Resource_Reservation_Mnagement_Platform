package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.entity.CheckinCode;
import com.campus.reservation.mapper.CheckinCodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 资源当日验证码的生成与查询。
 * 独立成服务，供打卡流程与管理端展示共同复用，避免服务间循环依赖。
 */
@Service
@RequiredArgsConstructor
public class CheckinCodeService {

    private final CheckinCodeMapper checkinCodeMapper;

    /** 查询资源当日验证码（不存在返回 null，不产生副作用） */
    public CheckinCode findTodayCode(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        return checkinCodeMapper.selectOne(Wrappers.<CheckinCode>lambdaQuery()
                .eq(CheckinCode::getResourceId, resourceId)
                .eq(CheckinCode::getValidDate, LocalDate.now())
                .last("LIMIT 1"));
    }

    /** 获取资源当日验证码，不存在则生成（每个资源每天仅一条） */
    public CheckinCode ensureTodayCode(Long resourceId) {
        CheckinCode code = findTodayCode(resourceId);
        if (code != null) {
            return code;
        }
        LocalDate today = LocalDate.now();
        code = new CheckinCode();
        code.setResourceId(resourceId);
        code.setCode(String.valueOf(100000 + ThreadLocalRandom.current().nextInt(900000)));
        code.setValidDate(today);
        code.setUsed(0);
        code.setExpiresAt(today.atTime(23, 59, 59));
        checkinCodeMapper.insert(code);
        return code;
    }
}

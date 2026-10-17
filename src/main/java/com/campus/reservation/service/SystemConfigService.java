package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.entity.SystemConfig;
import com.campus.reservation.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统全局配置：目前仅"一键闭馆"开关，供管理员应对节假日/突发情况。
 * 闭馆期间普通用户不可创建预约，管理员不受限制。
 */
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    /** 当前是否处于闭馆状态（配置行不存在时视为开放） */
    public boolean isClosed() {
        SystemConfig config = systemConfigMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, SystemConfig.KEY_CAMPUS_CLOSED));
        return config != null && "1".equals(config.getConfigValue());
    }

    /** 设置闭馆/开放（幂等 upsert） */
    @Transactional
    public void setClosed(boolean closed) {
        SystemConfig config = systemConfigMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, SystemConfig.KEY_CAMPUS_CLOSED));
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(SystemConfig.KEY_CAMPUS_CLOSED);
            config.setConfigValue(closed ? "1" : "0");
            systemConfigMapper.insert(config);
        } else {
            config.setConfigValue(closed ? "1" : "0");
            systemConfigMapper.updateById(config);
        }
    }
}

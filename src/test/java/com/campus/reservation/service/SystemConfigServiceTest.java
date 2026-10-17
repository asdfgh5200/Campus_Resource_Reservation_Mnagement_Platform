package com.campus.reservation.service;

import com.campus.reservation.entity.SystemConfig;
import com.campus.reservation.mapper.SystemConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @InjectMocks
    private SystemConfigService service;

    @Test
    @DisplayName("isClosed：不存在配置行视为开放")
    void isClosed_missingRow_returnsFalse() {
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        assertThat(service.isClosed()).isFalse();
    }

    @Test
    @DisplayName("isClosed：configValue=1 返回 true")
    void isClosed_valueOne_returnsTrue() {
        SystemConfig cfg = new SystemConfig();
        cfg.setConfigKey(SystemConfig.KEY_CAMPUS_CLOSED);
        cfg.setConfigValue("1");
        when(systemConfigMapper.selectOne(any())).thenReturn(cfg);
        assertThat(service.isClosed()).isTrue();
    }

    @Test
    @DisplayName("setClosed(true)：不存在时 insert、存在时 updateById")
    void setClosed_upsert() {
        // 无行 → insert
        when(systemConfigMapper.selectOne(any())).thenReturn(null);
        service.setClosed(true);
        ArgumentCaptor<SystemConfig> cap = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).insert(cap.capture());
        assertThat(cap.getValue().getConfigValue()).isEqualTo("1");
        verify(systemConfigMapper, never()).updateById(any(SystemConfig.class));

        // 已有行 → update
        SystemConfig existing = new SystemConfig();
        existing.setConfigKey(SystemConfig.KEY_CAMPUS_CLOSED);
        existing.setConfigValue("1");
        when(systemConfigMapper.selectOne(any())).thenReturn(existing);
        service.setClosed(false);
        assertThat(existing.getConfigValue()).isEqualTo("0");
        verify(systemConfigMapper, times(1)).updateById(existing);
    }
}

package com.campus.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_config")
public class SystemConfig {

    /** 一键闭馆开关的配置键 */
    public static final String KEY_CAMPUS_CLOSED = "campus_closed";

    @TableId(type = IdType.INPUT)
    private String configKey;

    private String configValue;

    private LocalDateTime updatedAt;
}

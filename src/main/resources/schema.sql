-- 校园资源预约平台 数据库初始化脚本（MySQL 8.x, utf8mb4）
-- 重复执行安全：全部使用 IF NOT EXISTS

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`      VARCHAR(128) NOT NULL COMMENT '密码（BCrypt）',
    `nickname`      VARCHAR(50)  DEFAULT NULL COMMENT '姓名/昵称',
    `role`          TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0 用户、1 管理员',
    `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `credit_score`  INT          NOT NULL DEFAULT 100 COMMENT '信用分',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常、0 禁用',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '用户表';

CREATE TABLE IF NOT EXISTS `resource` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(100)  NOT NULL COMMENT '资源名称',
    `resource_type` TINYINT       NOT NULL COMMENT '类型：1 教室、2 实验室、3 图书馆、4 体育馆',
    `building`      VARCHAR(50)   DEFAULT NULL COMMENT '所在楼宇',
    `floor`         VARCHAR(20)   DEFAULT NULL COMMENT '楼层',
    `room_no`       VARCHAR(50)   DEFAULT NULL COMMENT '房间号',
    `capacity`     INT          NOT NULL DEFAULT 0 COMMENT '容纳数量（实验室统一 10 人；体育馆按各自单位）',
    `unit`         VARCHAR(10)  NOT NULL DEFAULT '人' COMMENT '计量单位：人/台/片等',
    `longitude`     DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `open_time`     TIME          NOT NULL DEFAULT '08:00:00' COMMENT '开放时间',
    `close_time`    TIME          NOT NULL DEFAULT '22:00:00' COMMENT '关闭时间',
    `max_duration`  INT           NOT NULL DEFAULT 120 COMMENT '单次预约时长上限（分钟）',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 可预约、2 使用中、3 维修中',
    `description`   VARCHAR(500)  DEFAULT NULL COMMENT '描述',
    `created_at`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_type_status` (`resource_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '资源表';

CREATE TABLE IF NOT EXISTS `reservation` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT       NOT NULL COMMENT '预约用户 ID',
    `resource_id`  BIGINT       NOT NULL COMMENT '资源 ID',
    `start_time`   DATETIME     NOT NULL COMMENT '开始时间（半开区间左端）',
    `end_time`     DATETIME     NOT NULL COMMENT '结束时间（半开区间右端）',
    `checkin_type` TINYINT      DEFAULT 2 COMMENT '打卡方式：固定 2（验证码）',
    `checkin_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已打卡：0 否、1 是',
    `checkin_time` DATETIME     DEFAULT NULL COMMENT '打卡时间',
    `away_until`   DATETIME     DEFAULT NULL COMMENT '暂离截止时间（非空表示暂离中）',
    `headcount`    INT          NOT NULL DEFAULT 1 COMMENT '使用人数（体育馆多人预约用）',
    `remark`       VARCHAR(255) DEFAULT NULL COMMENT '备注',
    -- status: 1 待打卡、2 使用中、3 已完成、4 已取消、5 爽约、6 违规
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_resource_time` (`resource_id`, `start_time`, `end_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '预约表';

CREATE TABLE IF NOT EXISTS `course_schedule` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_id`     BIGINT       NOT NULL COMMENT '教室（resource.id）',
    `course_name` VARCHAR(100) NOT NULL COMMENT '课程名称',
    `day_of_week` TINYINT      NOT NULL COMMENT '星期：1~7',
    `start_time`  TIME         NOT NULL COMMENT '上课时间',
    `end_time`    TIME         NOT NULL COMMENT '下课时间',
    `teacher`     VARCHAR(50)  DEFAULT NULL COMMENT '教师',
    `term`        VARCHAR(20)  DEFAULT NULL COMMENT '学期',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_room_day` (`room_id`, `day_of_week`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '课表记录表';

CREATE TABLE IF NOT EXISTS `checkin_code` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `resource_id` BIGINT      NOT NULL COMMENT '资源 ID',
    `code`        VARCHAR(20) NOT NULL COMMENT '动态验证码',
    `valid_date`  DATE        NOT NULL COMMENT '有效日期',
    `used`        TINYINT     NOT NULL DEFAULT 0 COMMENT '是否使用：0 否、1 是',
    `expires_at`  DATETIME    NOT NULL COMMENT '过期时间',
    `created_at`  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_res_date` (`resource_id`, `valid_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '打卡验证码表';

CREATE TABLE IF NOT EXISTS `violation` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL COMMENT '违规用户 ID',
    `reservation_id`  BIGINT       DEFAULT NULL COMMENT '关联预约 ID',
    -- type: 1 爽约、2 超时使用、3 恶意取消、4 其他
    `type`            TINYINT      NOT NULL COMMENT '违规类型',
    `description`     VARCHAR(500) DEFAULT NULL COMMENT '违规描述',
    `deduct_score`    INT          NOT NULL DEFAULT 0 COMMENT '扣除信用分',
    -- status: 1 有效、0 已撤销
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '违规记录表';

CREATE TABLE IF NOT EXISTS `repair_order` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `resource_id` BIGINT       NOT NULL COMMENT '报修资源 ID',
    `user_id`     BIGINT       NOT NULL COMMENT '报修人 ID',
    `problem`     VARCHAR(500) NOT NULL COMMENT '问题描述',
    `image_url`   VARCHAR(255) DEFAULT NULL COMMENT '图片',
    -- status: 0 待处理、1 处理中、2 已完成、3 已驳回
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '工单状态',
    `handle_user` BIGINT       DEFAULT NULL COMMENT '处理人 ID',
    `handle_time` DATETIME     DEFAULT NULL COMMENT '处理时间',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_resource` (`resource_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '维修工单表';

CREATE TABLE IF NOT EXISTS `holiday` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`       VARCHAR(50) NOT NULL COMMENT '节假日名称',
    `start_date` DATE        NOT NULL COMMENT '开始日期',
    `end_date`   DATE        NOT NULL COMMENT '结束日期',
    `created_at` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_dates` (`start_date`, `end_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '节假日表';

CREATE TABLE IF NOT EXISTS `reservation_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT       NOT NULL COMMENT '操作用户 ID',
    `username`       VARCHAR(50)  DEFAULT NULL COMMENT '操作用户名（冗余，防用户删除后丢失）',
    `nickname`       VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名（冗余）',
    `reservation_id` BIGINT       DEFAULT NULL COMMENT '关联预约 ID',
    `resource_id`    BIGINT       DEFAULT NULL COMMENT '关联资源 ID',
    `resource_name`  VARCHAR(100) DEFAULT NULL COMMENT '资源名称（冗余）',
    -- action: 1 创建预约、2 取消预约、3 打卡、4 爽约、5 完成、6 违规
    `action`         TINYINT      NOT NULL COMMENT '操作类型',
    `detail`         VARCHAR(500) DEFAULT NULL COMMENT '操作详情',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '预约操作日志表';

CREATE TABLE IF NOT EXISTS `system_config` (
    `config_key`   VARCHAR(50)  NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(200) NOT NULL COMMENT '配置值',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '系统配置表（一键闭馆等全局开关）';

CREATE TABLE IF NOT EXISTS `campus_closure` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `resource_type` TINYINT      NOT NULL COMMENT '闭馆类型：2 实验室、3 图书馆、4 体育馆',
    `closure_date`  DATE         NOT NULL COMMENT '闭馆日期',
    `start_time`    TIME         NOT NULL COMMENT '闭馆开始时刻',
    `end_time`      TIME         NOT NULL COMMENT '闭馆结束时刻',
    `reason`        VARCHAR(255) DEFAULT NULL COMMENT '闭馆原因',
    `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否生效：1 生效、0 已撤销',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_type_date` (`resource_type`, `closure_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '按资源类型的定时闭馆';

CREATE TABLE IF NOT EXISTS `user_message` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT        NOT NULL COMMENT '所属用户 ID（留言=发起人，回复/通知=接收人）',
    -- direction: 1 用户→管理员（留言/咨询）、2 管理员或系统→用户（回复/通知）
    `direction`  TINYINT       NOT NULL DEFAULT 1 COMMENT '方向：1 用户→管理员、2 管理员/系统→用户',
    `content`    VARCHAR(1000) NOT NULL COMMENT '消息内容',
    `ref_id`     BIGINT        DEFAULT NULL COMMENT '回复指向的原留言 ID',
    `is_read`    TINYINT       NOT NULL DEFAULT 0 COMMENT '0 未读、1 已读',
    `created_at` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_direction_read` (`direction`, `is_read`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '站内消息表（用户↔管理员）';

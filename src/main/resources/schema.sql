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
    `credit_score`  INT          NOT NULL DEFAULT 100 COMMENT '信用分（历史字段，界面不再展示）',
    `cancel_count`  INT          NOT NULL DEFAULT 0 COMMENT '累计取消预约次数（上限 5，达标记恶意取消）',
    `reservation_total` INT     NOT NULL DEFAULT 0 COMMENT '累计预约次数（随预约创建自增，管理端可修正）',
    `restrict_override` TINYINT NOT NULL DEFAULT 0 COMMENT '预约权限覆盖：0 按违规规则、1 强制正常、2 强制受限',
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
    `campus`        VARCHAR(20)   DEFAULT NULL COMMENT '校区：马房山校区/南湖校区/余家头校区/军山校区',
    `building`      VARCHAR(50)   DEFAULT NULL COMMENT '所在楼宇',
    `floor`         VARCHAR(20)   DEFAULT NULL COMMENT '楼层',
    `room_type`     VARCHAR(32)   DEFAULT NULL COMMENT '细分类型：教室类型/实验方向/运动类型/座位类型',
    `room_no`       VARCHAR(50)   DEFAULT NULL COMMENT '房间号',
    `capacity`     INT          NOT NULL DEFAULT 0 COMMENT '容纳数量（实验室统一 10 人；体育馆按各自单位）',
    `unit`         VARCHAR(10)  NOT NULL DEFAULT '人' COMMENT '计量单位：人/台/片等',
    `longitude`     DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `open_time`     TIME          NOT NULL DEFAULT '08:00:00' COMMENT '开放时间',
    `close_time`    TIME          NOT NULL DEFAULT '22:00:00' COMMENT '关闭时间',
    `max_duration`  INT           NOT NULL DEFAULT 120 COMMENT '单次预约时长上限（分钟）',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 可预约、2 使用中、3 维修中、4 报修中',
    `description`   VARCHAR(500)  DEFAULT NULL COMMENT '描述',
    `created_at`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_type_status` (`resource_type`, `status`),
    KEY `idx_room_type` (`room_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '资源表';

-- 定位打卡基准点：WGS-84（与浏览器 Geolocation 坐标系一致）。
-- 坐标按「校区 + 楼栋」维护：同楼栋资源共享同一坐标，可在资源档案里现场校准（取当前位置）。
-- 这里只做空值兜底，不会覆盖管理员在资源档案里校准过的坐标。
-- 坐标为「纬度, 经度」。
UPDATE `resource`
SET `latitude` = CASE `resource_type`
        WHEN 1 THEN 30.511442
        WHEN 2 THEN 30.514356
        WHEN 3 THEN 30.513169
        WHEN 4 THEN 30.504850
    END,
    `longitude` = CASE `resource_type`
        WHEN 1 THEN 114.333556
        WHEN 2 THEN 114.345786
        WHEN 3 THEN 114.328458
        WHEN 4 THEN 114.330324
    END
WHERE `resource_type` IN (1, 2, 3, 4)
  AND (`latitude` IS NULL OR `longitude` IS NULL);

CREATE TABLE IF NOT EXISTS `reservation` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT       NOT NULL COMMENT '预约用户 ID',
    `resource_id`  BIGINT       NOT NULL COMMENT '资源 ID',
    `start_time`   DATETIME     NOT NULL COMMENT '开始时间（半开区间左端）',
    `end_time`     DATETIME     NOT NULL COMMENT '结束时间（半开区间右端）',
    `checkin_type` TINYINT      DEFAULT 1 COMMENT '打卡方式：1 定位、2 验证码（管理员补签）',
    `checkin_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已打卡：0 否、1 是',
    `checkin_time` DATETIME     DEFAULT NULL COMMENT '打卡时间',
    `checkin_latitude`  DECIMAL(10,6) DEFAULT NULL COMMENT '打卡时用户纬度（WGS-84）',
    `checkin_longitude` DECIMAL(10,6) DEFAULT NULL COMMENT '打卡时用户经度（WGS-84）',
    `checkin_distance`  INT          DEFAULT NULL COMMENT '打卡时距基准点距离（米）',
    `checkin_accuracy`  INT          DEFAULT NULL COMMENT '打卡时定位精度（米）',
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
    `reject_reason` VARCHAR(200) DEFAULT NULL COMMENT '驳回原因（随通知发给报修人）',
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
    -- action: 1 创建预约、2 取消预约、3 打卡、4 爽约、5 完成、6 提前释放、9 删除记录、10 管理员调整（7/8 为已下线的暂离，仅历史数据）
    `action`         TINYINT      NOT NULL COMMENT '操作类型',
    `operator_id`    BIGINT       DEFAULT NULL COMMENT '操作人 ID（管理员调整时记录管理员）',
    `operator_name`  VARCHAR(50)  DEFAULT NULL COMMENT '操作人名称（冗余）',
    `operator_role`  TINYINT      NOT NULL DEFAULT 0 COMMENT '操作人角色：0 用户、1 管理员、2 系统',
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
    `resource_id`   BIGINT       DEFAULT NULL COMMENT '闭馆范围：NULL=整类，非空=指定具体资源',
    `closure_date`  DATE         NOT NULL COMMENT '闭馆日期',
    `start_time`    TIME         NOT NULL COMMENT '闭馆开始时刻',
    `end_time`      TIME         NOT NULL COMMENT '闭馆结束时刻',
    `reason`        VARCHAR(255) DEFAULT NULL COMMENT '闭馆原因',
    `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否生效：1 生效、0 已撤销',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_type_date` (`resource_type`, `closure_date`),
    KEY `idx_resource` (`resource_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '按资源类型/具体资源的定时闭馆';

CREATE TABLE IF NOT EXISTS `user_message` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT        NOT NULL COMMENT '所属用户 ID（留言=发起人，回复/通知=接收人）',
    -- direction: 1 用户→管理员（留言/咨询）、2 管理员或系统→用户（回复/通知）
    `direction`  TINYINT       NOT NULL DEFAULT 1 COMMENT '方向：1 用户→管理员、2 管理员/系统→用户',
    `content`    VARCHAR(1000) NOT NULL COMMENT '消息内容',
    `ref_id`     BIGINT        DEFAULT NULL COMMENT '回复指向的原留言 ID',
    `resource_id` BIGINT       DEFAULT NULL COMMENT '关联资源：维修进度等通知可跳转到地点',
    `is_read`    TINYINT       NOT NULL DEFAULT 0 COMMENT '0 未读、1 已读',
    `created_at` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_direction_read` (`direction`, `is_read`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '站内消息表（用户↔管理员）';

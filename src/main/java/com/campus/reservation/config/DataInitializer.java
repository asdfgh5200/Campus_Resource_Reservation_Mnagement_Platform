package com.campus.reservation.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.CheckinGeofence;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import com.campus.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首次启动时初始化演示数据：1 个管理员账号、真实校区资源、真实课表、3 条示例预约。
 *
 * <p>资源与课表来自教务系统采集，以 classpath 下的 CSV 文件提供：
 * <ul>
 *   <li>{@code db/campus_rooms.csv}：校区|楼栋|楼层|房号|教室全名|类型|容量|细分类型</li>
 *   <li>{@code db/campus_courses.csv}：教室全名|课程|教师|星期|开始节|结束节</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** 课表学期标识 */
    private static final String TERM = "2026-2027-1";

    /** 固定管理员账号：学号 admin / 密码 admin / 姓名 管理员 */
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String ADMIN_NICKNAME = "管理员";

    /** 节次对应的上课时刻，下标即节次（1~13） */
    private static final LocalTime[] JC_START = {
            null,
            LocalTime.of(8, 0), LocalTime.of(8, 50), LocalTime.of(9, 55), LocalTime.of(10, 45),
            LocalTime.of(11, 35), LocalTime.of(14, 0), LocalTime.of(14, 50), LocalTime.of(15, 55),
            LocalTime.of(16, 45), LocalTime.of(18, 30), LocalTime.of(19, 20), LocalTime.of(20, 10),
            LocalTime.of(21, 0)
    };

    /** 节次对应的下课时刻，下标即节次（1~13） */
    private static final LocalTime[] JC_END = {
            null,
            LocalTime.of(8, 45), LocalTime.of(9, 35), LocalTime.of(10, 40), LocalTime.of(11, 30),
            LocalTime.of(12, 20), LocalTime.of(14, 45), LocalTime.of(15, 35), LocalTime.of(16, 40),
            LocalTime.of(17, 30), LocalTime.of(19, 15), LocalTime.of(20, 5), LocalTime.of(20, 55),
            LocalTime.of(21, 45)
    };

    private final SysUserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 老库升级：room_type 是后加的列，schema.sql 的建表语句不会给已存在的表补列
        ensureRoomTypeColumn();
        // 老库升级：sys_user 的取消次数列同理
        ensureUserColumns();
        // 老库升级：预约的打卡定位留痕列、日志的操作人列、闭馆的资源维度列
        ensureCheckinTraceColumns();
        ensureLogOperatorColumns();
        ensureClosureResourceColumn();
        ensureRepairColumns();
        // 暂离功能已下线：老库中残留的 away_until 列不再被任何代码读写，启动时清理
        dropColumnIfExists("reservation", "away_until");
        if (userMapper.selectCount(null) > 0) {
            // 保证固定管理员可用（账号不存在则创建，姓名/角色/状态不规范则纠正）
            ensureAdminAccount();
            // 老库升级：不重建数据，直接把 CSV 的细分类型同步回资源表
            syncRoomTypes();
            return;
        }
        log.info("检测到空库，开始初始化演示数据...");

        SysUser admin = newAdmin();
        userMapper.insert(admin);

        List<Resource> resources = buildResources();
        resources.forEach(resourceMapper::insert);

        Map<String, Long> idByName = new HashMap<>();
        for (Resource r : resources) {
            idByName.put(r.getName(), r.getId());
        }
        int courseCount = seedCourses(idByName);
        seedDemoReservations(resources, admin.getId());

        log.info("演示数据初始化完成：1 个用户，{} 个资源，{} 条课表，3 条预约", resources.size(), courseCount);
    }

    /** 固定管理员：姓名 管理员、学号/账号 admin、密码 admin，管理员页面依此初始数据 */
    private SysUser newAdmin() {
        SysUser admin = new SysUser();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword(encoder.encode(ADMIN_PASSWORD));
        admin.setNickname(ADMIN_NICKNAME);
        admin.setRole(1);
        admin.setPhone("13800000000");
        admin.setEmail("admin@campus.edu");
        admin.setCreditScore(100);
        admin.setStatus(1);
        return admin;
    }

    /**
     * 保证固定管理员账号存在且可用：不存在则创建，存在则纠正姓名/角色/状态，
     * 密码不是约定值时重置为 admin（约定：姓名 管理员 / 学号 admin / 密码 admin）。
     */
    private void ensureAdminAccount() {
        SysUser admin = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, ADMIN_USERNAME));
        if (admin == null) {
            userMapper.insert(newAdmin());
            log.info("已创建固定管理员账号 {}/{}", ADMIN_USERNAME, ADMIN_PASSWORD);
            return;
        }
        SysUser patch = new SysUser();
        patch.setId(admin.getId());
        boolean changed = false;
        if (!ADMIN_NICKNAME.equals(admin.getNickname())) {
            patch.setNickname(ADMIN_NICKNAME);
            changed = true;
        }
        if (admin.getPassword() == null || !encoder.matches(ADMIN_PASSWORD, admin.getPassword())) {
            patch.setPassword(encoder.encode(ADMIN_PASSWORD));
            changed = true;
        }
        if (admin.getRole() == null || admin.getRole() != 1) {
            patch.setRole(1);
            changed = true;
        }
        if (admin.getStatus() == null || admin.getStatus() != 1) {
            patch.setStatus(1);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(patch);
            log.info("管理员账号 {} 已规范化：姓名 {} / 密码与约定一致", ADMIN_USERNAME, ADMIN_NICKNAME);
        }
    }

    /**
     * 确保 sys_user 的统计列存在：取消次数、累计预约、预约权限覆盖。
     * 与 room_type 同理：建表语句不会给已存在的表补列，必须在任何用户查询之前执行。
     */
    private void ensureUserColumns() {
        addColumnIfMissing("sys_user", "cancel_count",
                "ALTER TABLE `sys_user` ADD COLUMN `cancel_count` INT NOT NULL DEFAULT 0 "
                        + "COMMENT '累计取消预约次数（上限 5）' AFTER `credit_score`");
        boolean addedTotal = addColumnIfMissing("sys_user", "reservation_total",
                "ALTER TABLE `sys_user` ADD COLUMN `reservation_total` INT NOT NULL DEFAULT 0 "
                        + "COMMENT '累计预约次数（随预约创建自增，管理端可修正）' AFTER `cancel_count`");
        addColumnIfMissing("sys_user", "restrict_override",
                "ALTER TABLE `sys_user` ADD COLUMN `restrict_override` TINYINT NOT NULL DEFAULT 0 "
                        + "COMMENT '预约权限覆盖：0 按违规规则、1 强制正常、2 强制受限' AFTER `reservation_total`");
        // 新列首次出现时用真实预约记录回填，旧库升级后「累计预约」不会是 0
        if (addedTotal) {
            jdbcTemplate.update("UPDATE `sys_user` u SET u.`reservation_total` = "
                    + "(SELECT COUNT(*) FROM `reservation` r WHERE r.`user_id` = u.`id` AND r.`deleted` = 0)");
            log.info("sys_user.reservation_total 已按现有预约记录回填");
        }
        // 历史脏数据修正：旧代码递增取消次数时未封顶，出现过 6/5、7/5 这类越界值，统一收回到上限
        int capped = jdbcTemplate.update("UPDATE `sys_user` SET `cancel_count` = ? WHERE `cancel_count` > ?",
                ReservationService.CANCEL_LIMIT, ReservationService.CANCEL_LIMIT);
        if (capped > 0) {
            log.info("{} 个用户的取消次数超过上限，已收回到 {}", capped, ReservationService.CANCEL_LIMIT);
        }
    }

    /**
     * 确保预约表的打卡留痕列存在：用户打卡坐标、距离与定位精度。
     * 管理端「预约详情」靠这些列还原当时到底在哪儿打的卡。
     */
    private void ensureCheckinTraceColumns() {
        addColumnIfMissing("reservation", "checkin_latitude",
                "ALTER TABLE `reservation` ADD COLUMN `checkin_latitude` DECIMAL(10,6) DEFAULT NULL "
                        + "COMMENT '打卡时用户纬度（WGS-84）' AFTER `checkin_time`");
        addColumnIfMissing("reservation", "checkin_longitude",
                "ALTER TABLE `reservation` ADD COLUMN `checkin_longitude` DECIMAL(10,6) DEFAULT NULL "
                        + "COMMENT '打卡时用户经度（WGS-84）' AFTER `checkin_latitude`");
        addColumnIfMissing("reservation", "checkin_distance",
                "ALTER TABLE `reservation` ADD COLUMN `checkin_distance` INT DEFAULT NULL "
                        + "COMMENT '打卡时距基准点距离（米）' AFTER `checkin_longitude`");
        addColumnIfMissing("reservation", "checkin_accuracy",
                "ALTER TABLE `reservation` ADD COLUMN `checkin_accuracy` INT DEFAULT NULL "
                        + "COMMENT '打卡时定位精度（米）' AFTER `checkin_distance`");
    }

    /**
     * 确保操作日志表的操作人列存在。
     * 旧日志只知道「预约归属用户」，新列首次补齐时按归属用户回填，管理员调整类回填为管理员。
     */
    private void ensureLogOperatorColumns() {
        addColumnIfMissing("reservation_log", "operator_id",
                "ALTER TABLE `reservation_log` ADD COLUMN `operator_id` BIGINT DEFAULT NULL "
                        + "COMMENT '操作人 ID（管理员调整时记录管理员）' AFTER `action`");
        addColumnIfMissing("reservation_log", "operator_name",
                "ALTER TABLE `reservation_log` ADD COLUMN `operator_name` VARCHAR(50) DEFAULT NULL "
                        + "COMMENT '操作人名称（冗余）' AFTER `operator_id`");
        boolean addedRole = addColumnIfMissing("reservation_log", "operator_role",
                "ALTER TABLE `reservation_log` ADD COLUMN `operator_role` TINYINT NOT NULL DEFAULT 0 "
                        + "COMMENT '操作人角色：0 用户、1 管理员、2 系统' AFTER `operator_name`");
        if (addedRole) {
            jdbcTemplate.update("UPDATE `reservation_log` SET `operator_id` = `user_id`, "
                    + "`operator_name` = IFNULL(`nickname`, `username`) WHERE `operator_id` IS NULL");
            jdbcTemplate.update("UPDATE `reservation_log` SET `operator_role` = 1 WHERE `action` = 10");
            log.info("reservation_log 操作人列已补齐，历史日志已按归属用户回填");
        }
    }

    /** 确保闭馆表支持「只闭某个具体资源」：新增 resource_id，空值表示整类闭馆 */
    private void ensureClosureResourceColumn() {
        addColumnIfMissing("campus_closure", "resource_id",
                "ALTER TABLE `campus_closure` ADD COLUMN `resource_id` BIGINT DEFAULT NULL "
                        + "COMMENT '闭馆范围：NULL=整类，非空=指定具体资源' AFTER `resource_type`");
    }

    /**
     * 确保维修工单的驳回原因列、站内消息的关联资源列存在。
     * 两张表都是先有数据后加列，建表语句不会给旧表补列，必须启动时补齐。
     */
    private void ensureRepairColumns() {
        addColumnIfMissing("repair_order", "reject_reason",
                "ALTER TABLE `repair_order` ADD COLUMN `reject_reason` VARCHAR(200) DEFAULT NULL "
                        + "COMMENT '驳回原因（随通知发给报修人）' AFTER `status`");
        addColumnIfMissing("user_message", "resource_id",
                "ALTER TABLE `user_message` ADD COLUMN `resource_id` BIGINT DEFAULT NULL "
                        + "COMMENT '关联资源：维修进度等通知可跳转到地点' AFTER `ref_id`");
    }

    /** 查元数据后按需补列：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS；返回是否真的补了列 */
    private boolean addColumnIfMissing(String table, String column, String ddl) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (exists != null && exists > 0) {
            return false;
        }
        jdbcTemplate.execute(ddl);
        log.info("{}.{} 列缺失，已自动补齐", table, column);
        return true;
    }

    /** 查元数据后按需删列：用于已下线功能的废弃字段清理；返回是否真的删了列 */
    private boolean dropColumnIfExists(String table, String column) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (exists == null || exists == 0) {
            return false;
        }
        jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP COLUMN `" + column + "`");
        log.info("{}.{} 列已随功能下线删除", table, column);
        return true;
    }

    /**
     * 确保 resource 表存在 room_type 列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS， 这里查元数据后按需补列）。
     * 必须在任何 Resource 查询之前执行，否则会报 Unknown column 'room_type'。
     */
    private void ensureRoomTypeColumn() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource' AND COLUMN_NAME = 'room_type'",
                Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `resource` ADD COLUMN `room_type` VARCHAR(32) DEFAULT NULL "
                + "COMMENT '细分类型：教室类型/实验方向/运动类型/座位类型' AFTER `floor`");
        jdbcTemplate.execute("ALTER TABLE `resource` ADD KEY `idx_room_type` (`room_type`)");
        log.info("resource 表缺少 room_type 列，已自动补齐");
    }

    /**
     * 把 CSV 第 8 列的细分类型回填到已有资源上，只更新存在差异的记录。
     * 用于“新增 room_type 维度”的库升级：无需 DROP 重建，重启即可拿到最新类型。
     */
    private void syncRoomTypes() throws Exception {
        Map<String, String> wanted = new HashMap<>();
        try (BufferedReader br = open("db/campus_rooms.csv")) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split("\\|", -1);
                if (f.length > 7 && !f[7].isBlank()) {
                    wanted.put(f[4], f[7]);
                }
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        // 图书馆座位（type=3）的类型由代码固定为「自习座位」，不在 CSV 中
        List<Resource> all = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .ne(Resource::getResourceType, 3));
        int updated = 0;
        for (Resource r : all) {
            String type = wanted.get(r.getName());
            if (type == null || type.equals(r.getRoomType())) {
                continue;
            }
            // 只更新 room_type，避免整行回写覆盖其他字段
            Resource patch = new Resource();
            patch.setId(r.getId());
            patch.setRoomType(type);
            resourceMapper.updateById(patch);
            updated++;
        }
        if (updated > 0) {
            log.info("资源细分类型已同步：{} 条（教室类型 / 实验方向 / 运动类型）", updated);
        }
    }

    /** 读取采集到的真实校区资源，并补充图书馆自习座位 */
    private List<Resource> buildResources() throws Exception {
        List<Resource> list = new ArrayList<>();
        try (BufferedReader br = open("db/campus_rooms.csv")) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split("\\|", -1);
                if (f.length < 7) {
                    continue;
                }
                String campus = f[0];
                String building = f[1];
                String floor = f[2];
                String roomNo = f[3];
                String name = f[4];
                int type = Integer.parseInt(f[5]);
                int capacity = Integer.parseInt(f[6]);
                String roomType = f.length > 7 && !f[7].isBlank() ? f[7] : null;
                boolean gym = type == 4;
                boolean lab = type == 2;
                Resource r = base(name, type, campus, building, floor, roomNo, capacity,
                        gym ? LocalTime.of(9, 0) : LocalTime.of(8, 0),
                        gym ? LocalTime.of(21, 0) : LocalTime.of(22, 0),
                        gym ? 90 : (lab ? 180 : 120));
                r.setDescription(campus + " " + building + " " + floor + " " + roomNo + "，可容纳 " + capacity + " 人。");
                r.setRoomType(roomType);
                list.add(r);
            }
        }

        // 图书馆：6F / 7F 各 60 个多媒体自习位（座位级资源，单人预约）
        for (String floor : List.of("6F", "7F")) {
            for (int seat = 1; seat <= 60; seat++) {
                String roomNo = floor + "-" + String.format("%02d", seat);
                Resource r = base(roomNo + " 多媒体自习位", 3, "南湖校区", "图书馆", floor, roomNo,
                        1, LocalTime.of(8, 0), LocalTime.of(22, 0), 240);
                r.setUnit("人");
                r.setRoomType("自习座位");
                r.setDescription("图书馆 " + floor + " 多媒体自习室 " + String.format("%02d", seat)
                        + " 号座位，配备独立电脑与隔板。");
                list.add(r);
            }
        }
        return list;
    }

    /** 导入真实课表：教室全名匹配资源后批量写入 */
    private int seedCourses(Map<String, Long> idByName) throws Exception {
        List<Object[]> batch = new ArrayList<>();
        try (BufferedReader br = open("db/campus_courses.csv")) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split("\\|", -1);
                if (f.length < 6) {
                    continue;
                }
                Long roomId = idByName.get(f[0]);
                if (roomId == null) {
                    continue;
                }
                int day = Integer.parseInt(f[3]);
                int startJc = clampJc(Integer.parseInt(f[4]));
                int endJc = clampJc(Integer.parseInt(f[5]));
                batch.add(new Object[]{roomId, f[1], day, JC_START[startJc], JC_END[endJc], TERM});
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO course_schedule (room_id, course_name, day_of_week, start_time, end_time, term) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    batch);
        }
        return batch.size();
    }

    /** 三条示例预约分别挂在实验室、图书馆座位与体育馆，覆盖待打卡/已完成两种状态 */
    private void seedDemoReservations(List<Resource> resources, Long demoUserId) {
        Long labId = pickByType(resources, 2);
        Long seatId = pickByType(resources, 3);
        Long gymId = resources.stream()
                .filter(r -> r.getResourceType() == 4 && r.getName().contains("羽毛球"))
                .map(Resource::getId).findFirst()
                .orElseGet(() -> pickByType(resources, 4));
        if (labId == null || seatId == null || gymId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        insertReservation(demoUserId, labId, now.plusDays(1).toLocalDate().atTime(10, 0),
                now.plusDays(1).toLocalDate().atTime(12, 0), 2, 0, null, "小组讨论",
                Reservation.STATUS_PENDING, now.minusDays(1));
        insertReservation(demoUserId, seatId, now.minusDays(1).toLocalDate().atTime(14, 0),
                now.minusDays(1).toLocalDate().atTime(17, 0), 2, 1,
                now.minusDays(1).toLocalDate().atTime(13, 55), "自习", Reservation.STATUS_COMPLETED, now.minusDays(2));
        insertReservation(demoUserId, gymId, now.minusDays(3).toLocalDate().atTime(16, 0),
                now.minusDays(3).toLocalDate().atTime(18, 0), 2, 1,
                now.minusDays(3).toLocalDate().atTime(15, 58), "羽毛球活动", Reservation.STATUS_COMPLETED, now.minusDays(4));
    }

    private Long pickByType(List<Resource> resources, int type) {
        return resources.stream()
                .filter(r -> r.getResourceType() == type)
                .map(Resource::getId)
                .findFirst()
                .orElse(null);
    }

    private void insertReservation(Long userId, Long resourceId, LocalDateTime start, LocalDateTime end,
                                   int checkinType, int checkinFlag, LocalDateTime checkinTime,
                                   String remark, int status, LocalDateTime createdAt) {
        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setResourceId(resourceId);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setCheckinType(checkinType);
        r.setCheckinFlag(checkinFlag);
        r.setCheckinTime(checkinTime);
        r.setRemark(remark);
        r.setStatus(status);
        r.setCreatedAt(createdAt);
        r.setUpdatedAt(createdAt);
        reservationMapper.insert(r);
    }

    private Resource base(String name, int type, String campus, String building, String floor, String roomNo,
                          int capacity, LocalTime open, LocalTime close, int maxDuration) {
        Resource r = new Resource();
        r.setName(name);
        r.setResourceType(type);
        r.setCampus(campus);
        r.setBuilding(building);
        r.setFloor(floor);
        r.setRoomNo(roomNo);
        r.setCapacity(capacity);
        r.setUnit("人");
        // 经纬度：统一取定位打卡基准点（WGS-84），与 CheckinGeofence / schema.sql 保持一致
        CheckinGeofence.Point point = CheckinGeofence.of(type);
        r.setLatitude(BigDecimal.valueOf(point.latitude()).setScale(6, RoundingMode.HALF_UP));
        r.setLongitude(BigDecimal.valueOf(point.longitude()).setScale(6, RoundingMode.HALF_UP));
        r.setOpenTime(open);
        r.setCloseTime(close);
        r.setMaxDuration(maxDuration);
        r.setStatus(1);
        return r;
    }

    private static int clampJc(int jc) {
        return Math.max(1, Math.min(jc, JC_START.length - 1));
    }

    private BufferedReader open(String path) throws Exception {
        InputStream in = new ClassPathResource(path).getInputStream();
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}

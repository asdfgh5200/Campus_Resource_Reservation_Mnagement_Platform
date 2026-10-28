package com.campus.reservation.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.CheckinGeofence;
import com.campus.reservation.common.TermCalendar;
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
 * <p>资源与课表来自教务系统采集（按教室抓取全校 1263 间已排课教室的整学期课表），
 * 以 classpath 下的 CSV 文件提供：
 * <ul>
 *   <li>{@code db/campus_rooms.csv}：校区|楼栋|楼层|房号|教室全名|类型|容量|细分类型</li>
 *   <li>{@code db/campus_courses.csv}：教室全名|课程|教师|星期|开始节|结束节|周次位图</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** 课表学期标识 */
    private static final String TERM = TermCalendar.TERM;

    /** 课表导入版本：节次口径或数据源变更时提升，老库据此清空重建课表 */
    private static final String COURSE_IMPORT_VERSION = "2026-2027-1/jwxt-16节/按教室全量";
    /** 记录课表导入版本的 system_config 键 */
    private static final String COURSE_VERSION_KEY = "course_import_version";
    /** 课表插入语句：列顺序与 syncCourses / seedCourses 的入参数组一致 */
    private static final String INSERT_COURSE_SQL =
            "INSERT INTO course_schedule (room_id, course_name, teacher, day_of_week, start_time, end_time, term, weeks) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /** 固定管理员账号：学号 admin / 密码 admin / 姓名 管理员 */
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String ADMIN_NICKNAME = "管理员";

    /**
     * 节次对应的上课时刻，下标即教务节次（1~16），取自教务「节次查询」原表。
     * 教务把 6/7 节称中课1/中课2、13 节称晚课，全部原样保留，不再压缩成 13 节。
     */
    private static final LocalTime[] JC_START = {
            null,
            LocalTime.of(8, 0), LocalTime.of(8, 50), LocalTime.of(9, 55), LocalTime.of(10, 45),
            LocalTime.of(11, 35), LocalTime.of(12, 25), LocalTime.of(13, 10), LocalTime.of(14, 0),
            LocalTime.of(14, 50), LocalTime.of(15, 40), LocalTime.of(16, 45), LocalTime.of(17, 35),
            LocalTime.of(18, 20), LocalTime.of(19, 0), LocalTime.of(19, 50), LocalTime.of(20, 40)
    };

    /** 节次对应的下课时刻，下标即教务节次（1~16） */
    private static final LocalTime[] JC_END = {
            null,
            LocalTime.of(8, 45), LocalTime.of(9, 45), LocalTime.of(10, 40), LocalTime.of(11, 30),
            LocalTime.of(12, 20), LocalTime.of(13, 10), LocalTime.of(13, 55), LocalTime.of(14, 45),
            LocalTime.of(15, 35), LocalTime.of(16, 25), LocalTime.of(17, 30), LocalTime.of(18, 20),
            LocalTime.of(19, 0), LocalTime.of(19, 45), LocalTime.of(20, 35), LocalTime.of(21, 25)
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
        // 老库升级：课表周次位图列
        ensureCourseWeeksColumn();
        // 老库升级：课表列宽要能装下教务原始数据（多人联合授课的教师名）
        ensureCourseColumnWidths();
        // 暂离功能已下线：老库中残留的 away_until 列不再被任何代码读写，启动时清理
        dropColumnIfExists("reservation", "away_until");
        if (userMapper.selectCount(null) > 0) {
            // 保证固定管理员可用（账号不存在则创建，姓名/角色/状态不规范则纠正）
            ensureAdminAccount();
            // 老库升级：按 CSV 补齐教务新排课的教室（课表靠教室全名挂到 resource.id）
            syncRooms();
            // 老库升级：不重建数据，直接把 CSV 的细分类型同步回资源表
            syncRoomTypes();
            // 老库升级：按 CSV 增量补齐课表（新增教室 / 新增课程 / 周次变化）
            syncCourses();
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
        saveConfig(COURSE_VERSION_KEY, COURSE_IMPORT_VERSION);
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

    /**
     * 确保 course_schedule 有周次位图列。教务课表不是每周都上（如「8周」「13-15周(单)」），
     * 旧表只有星期与时刻，会把一学期一次的课当成每周占用。
     */
    private void ensureCourseWeeksColumn() {
        addColumnIfMissing("course_schedule", "weeks",
                "ALTER TABLE `course_schedule` ADD COLUMN `weeks` VARCHAR(40) DEFAULT NULL "
                        + "COMMENT '上课周次位图（第 n 位 1 = 第 n 周上课）' AFTER `term`");
    }

    /**
     * 确保课表列宽能装下教务原始数据：一门课可能有 20 多位联合授课教师（最长约 90 字符），
     * 旧的 {@code teacher VARCHAR(50)} 会直接报 Data too long 导致初始化中断。
     */
    private void ensureCourseColumnWidths() {
        widenColumn("course_schedule", "teacher", 200,
                "ALTER TABLE `course_schedule` MODIFY COLUMN `teacher` VARCHAR(200) DEFAULT NULL "
                        + "COMMENT '教师（多人时逗号分隔）'");
        widenColumn("course_schedule", "course_name", 200,
                "ALTER TABLE `course_schedule` MODIFY COLUMN `course_name` VARCHAR(200) NOT NULL "
                        + "COMMENT '课程名称'");
    }

    /** 列宽不足时放宽到指定长度（按字符数判断，与 MySQL VARCHAR 语义一致） */
    private void widenColumn(String table, String column, int minLength, String ddl) {
        List<Long> sizes = jdbcTemplate.queryForList(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class, table, column);
        if (sizes.isEmpty() || sizes.get(0) == null || sizes.get(0) >= minLength) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("{}.{} 列宽 {} 装不下教务数据，已放宽到 {}", table, column, sizes.get(0), minLength);
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

    /**
     * 资源 CSV 的一行。
     *
     * @param type     资源类型：1 教室、2 实验室、4 体育馆
     * @param roomType 细分类型（教室类型 / 实验方向 / 运动类型）
     */
    private record RoomRow(String campus, String building, String floor, String roomNo, String name,
                           int type, int capacity, String roomType) {
    }

    /** 读取资源 CSV（表头行跳过，列不足 7 或数字异常的行丢弃） */
    private List<RoomRow> readRoomCsv() throws Exception {
        List<RoomRow> rows = new ArrayList<>();
        try (BufferedReader br = open("db/campus_rooms.csv")) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split("\\|", -1);
                if (f.length < 7) {
                    continue;
                }
                try {
                    rows.add(new RoomRow(f[0], f[1], f[2], f[3], f[4], Integer.parseInt(f[5]),
                            Integer.parseInt(f[6]), f.length > 7 && !f[7].isBlank() ? f[7] : null));
                } catch (NumberFormatException e) {
                    log.warn("资源 CSV 行格式异常，已跳过：{}", line);
                }
            }
        }
        return rows;
    }

    /** CSV 行 → 资源实体（开放时间与单次时长按类型取默认值） */
    private Resource toResource(RoomRow row) {
        boolean gym = row.type() == 4;
        boolean lab = row.type() == 2;
        Resource r = base(row.name(), row.type(), row.campus(), row.building(), row.floor(), row.roomNo(),
                row.capacity(),
                gym ? LocalTime.of(9, 0) : LocalTime.of(8, 0),
                gym ? LocalTime.of(21, 0) : LocalTime.of(22, 0),
                gym ? 90 : (lab ? 180 : 120));
        r.setDescription(row.campus() + " " + row.building() + " " + row.floor() + " " + row.roomNo()
                + "，可容纳 " + row.capacity() + " 人。");
        r.setRoomType(row.roomType());
        return r;
    }

    /**
     * 老库升级：把 CSV 中新增的教室补进资源表。
     *
     * <p>课表按「教室全名」匹配 {@code resource.id}，资源缺失时该教室的整学期课会被静默跳过，
     * 因此教务新增排课的教室必须先把资源建出来，再同步课表。
     */
    private void syncRooms() throws Exception {
        Map<String, Long> existing = resourceIdByName();
        int added = 0;
        for (RoomRow row : readRoomCsv()) {
            if (existing.containsKey(row.name())) {
                continue;
            }
            resourceMapper.insert(toResource(row));
            added++;
        }
        if (added > 0) {
            log.info("资源表已按 CSV 补齐 {} 间教室（教务已排课但库里没有）", added);
        }
    }

    /** 读取采集到的真实校区资源，并补充图书馆自习座位 */
    private List<Resource> buildResources() throws Exception {
        List<Resource> list = new ArrayList<>();
        for (RoomRow row : readRoomCsv()) {
            list.add(toResource(row));
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

    // ==================== 课表导入 ====================

    /**
     * 课表 CSV 的一行。
     *
     * @param startJc 开始节次（教务 1~16）
     * @param endJc   结束节次（教务 1~16）
     * @param weeks   30 位周次位图，空表示每周上课（旧版 CSV 无此列）
     */
    private record CourseRow(String roomName, String course, String teacher, int day,
                             int startJc, int endJc, String weeks) {
    }

    /** 读取课表 CSV；旧版 CSV 只有 6 列，缺周次列时按「每周」处理 */
    private List<CourseRow> readCourseCsv() throws Exception {
        List<CourseRow> rows = new ArrayList<>();
        try (BufferedReader br = open("db/campus_courses.csv")) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split("\\|", -1);
                if (f.length < 6) {
                    continue;
                }
                try {
                    rows.add(new CourseRow(f[0], cleanText(f[1]), cleanText(f[2]), Integer.parseInt(f[3]),
                            clampJc(Integer.parseInt(f[4])), clampJc(Integer.parseInt(f[5])),
                            f.length > 6 && !f[6].isBlank() ? f[6].trim() : null));
                } catch (NumberFormatException e) {
                    log.warn("课表 CSV 行格式异常，已跳过：{}", line);
                }
            }
        }
        return rows;
    }

    /** 导入真实课表：教室全名匹配资源后批量写入（仅空库初始化调用） */
    private int seedCourses(Map<String, Long> idByName) throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (CourseRow row : readCourseCsv()) {
            Long roomId = idByName.get(row.roomName());
            if (roomId == null) {
                continue;
            }
            batch.add(courseParams(roomId, row));
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_COURSE_SQL, batch);
        }
        return batch.size();
    }

    /**
     * 老库升级：让库里的课表与 CSV 保持一致。
     *
     * <p>采集口径变化时（节次从压缩后的 13 节改成教务原生的 16 节、数据源换成按教室抓的全量
     * 课表），旧记录的时刻与新口径对不上，按 {@link #COURSE_IMPORT_VERSION} 清空重建一次；
     * 版本一致时只做增量：补缺失行、更新周次与教师有变化的行，管理员手工导入的课程不受影响。
     */
    private void syncCourses() throws Exception {
        List<CourseRow> rows = readCourseCsv();
        if (rows.isEmpty()) {
            log.warn("课表同步跳过：campus_courses.csv 读取为空");
            return;
        }
        Map<String, Long> idByName = resourceIdByName();
        String storedVersion = configValue(COURSE_VERSION_KEY);

        if (!COURSE_IMPORT_VERSION.equals(storedVersion)) {
            Integer old = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_schedule", Integer.class);
            jdbcTemplate.update("DELETE FROM course_schedule");
            int inserted = insertCourseBatch(rows, idByName);
            saveConfig(COURSE_VERSION_KEY, COURSE_IMPORT_VERSION);
            log.info("课表采集口径已变更（{} → {}），旧课表 {} 条重建为 {} 条", storedVersion,
                    COURSE_IMPORT_VERSION, old, inserted);
            return;
        }

        // 增量：库里现有记录按「教室+课程+星期+起止时刻」建索引。
        // 同一时间去重键可对应多行（同一门课多位教师分别排课），因此索引存的是列表，
        // 否则后一行会覆盖前一行，后面的课表行就找不到自己而重复插入。
        Map<String, List<Object[]>> existing = new HashMap<>();
        for (Map<String, Object> m : jdbcTemplate.queryForList(
                "SELECT id, room_id, course_name, day_of_week, start_time, end_time, teacher, weeks "
                        + "FROM course_schedule")) {
            String key = courseKey(((Number) m.get("room_id")).longValue(), String.valueOf(m.get("course_name")),
                    ((Number) m.get("day_of_week")).intValue(), toJcTime(m.get("start_time")),
                    toJcTime(m.get("end_time")));
            existing.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new Object[]{((Number) m.get("id")).longValue(), m.get("teacher"), m.get("weeks")});
        }

        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (CourseRow row : rows) {
            Long roomId = idByName.get(row.roomName());
            if (roomId == null) {
                continue;
            }
            String key = courseKey(roomId, row.course(), row.day(),
                    hm(JC_START[row.startJc()]), hm(JC_END[row.endJc()]));
            Object[] hit = takeExisting(existing.get(key), row);
            if (hit == null) {
                inserts.add(courseParams(roomId, row));
            } else if (!sameText(row.teacher(), (String) hit[1]) || !sameText(row.weeks(), (String) hit[2])) {
                updates.add(new Object[]{row.teacher(), row.weeks(), hit[0]});
            }
        }
        if (!inserts.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_COURSE_SQL, inserts);
        }
        if (!updates.isEmpty()) {
            jdbcTemplate.batchUpdate("UPDATE course_schedule SET teacher = ?, weeks = ? WHERE id = ?", updates);
        }
        if (!inserts.isEmpty() || !updates.isEmpty()) {
            log.info("课表已按 CSV 增量同步：新增 {} 条（新教室/新课程），更新周次与教师 {} 条",
                    inserts.size(), updates.size());
        }
    }

    /** 分批写入课表，返回成功条数 */
    private int insertCourseBatch(List<CourseRow> rows, Map<String, Long> idByName) {
        int inserted = 0;
        List<Object[]> batch = new ArrayList<>();
        for (CourseRow row : rows) {
            Long roomId = idByName.get(row.roomName());
            if (roomId == null) {
                continue;
            }
            batch.add(courseParams(roomId, row));
            if (batch.size() >= 1000) {
                jdbcTemplate.batchUpdate(INSERT_COURSE_SQL, batch);
                inserted += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_COURSE_SQL, batch);
            inserted += batch.size();
        }
        return inserted;
    }

    private Object[] courseParams(Long roomId, CourseRow row) {
        return new Object[]{roomId, row.course(), row.teacher(), row.day(),
                JC_START[row.startJc()], JC_END[row.endJc()], TERM, row.weeks()};
    }

    /** 课表去重键：教室 + 课程 + 星期 + 起止时刻（与 CSV 同一口径） */
    private String courseKey(Long roomId, String course, int day, String start, String end) {
        return roomId + "|" + course + "|" + day + "|" + start + "|" + end;
    }

    /**
     * 从同一去重键的候选行里取走一行：优先教师与周次都对得上（无变化），
     * 否则取任意一行当作待更新目标。取走即从列表移除，保证 CSV 的每行只匹配一次。
     */
    private Object[] takeExisting(List<Object[]> bucket, CourseRow row) {
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        for (int i = 0; i < bucket.size(); i++) {
            Object[] candidate = bucket.get(i);
            if (sameText(row.teacher(), (String) candidate[1])
                    && sameText(row.weeks(), (String) candidate[2])) {
                return bucket.remove(i);
            }
        }
        return bucket.remove(0);
    }

    /** 资源名称 → id，供课表 CSV 的教室全名匹配 */
    private Map<String, Long> resourceIdByName() {
        Map<String, Long> idByName = new HashMap<>();
        for (Resource r : resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .select(Resource::getId, Resource::getName))) {
            idByName.put(r.getName(), r.getId());
        }
        return idByName;
    }

    /** 读 system_config 的字符串值，不存在返回 null */
    private String configValue(String key) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT config_value FROM system_config WHERE config_key = ?", String.class, key);
        return values.isEmpty() ? null : values.get(0);
    }

    /** 写 system_config（存在则更新） */
    private void saveConfig(String key, String value) {
        jdbcTemplate.update("INSERT INTO system_config (config_key, config_value) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)", key, value);
    }

    /** null 与空串视为同值，避免每次重启都重复写库 */
    private static boolean sameText(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    /** 节次换算后的时刻串，与 CSV 换算结果同一口径 */
    private String toJcTime(Object dbTime) {
        String s = String.valueOf(dbTime);
        return s.length() >= 5 ? s.substring(0, 5) : "00:00";
    }

    /**
     * 字段清洗：教务原始数据里偶有被折行的课程名（字段内换行/制表符），
     * 空白统一压成单个空格，既能正常入库，也便于与库里已有记录比对。
     */
    private static String cleanText(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    /** LocalTime(14:00:00) → 14:00，与数据库 TIME 串对齐 */
    private String hm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
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

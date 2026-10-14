package com.campus.reservation.config;

import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首次启动时初始化演示数据：1 个管理员账号、286 个资源、3 条示例预约。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final ReservationMapper reservationMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        log.info("检测到空库，开始初始化演示数据...");

        // ---- 用户 ----
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(encoder.encode("admin"));
        admin.setNickname("系统管理员");
        admin.setRole(1);
        admin.setPhone("13800000000");
        admin.setEmail("admin@campus.edu");
        admin.setCreditScore(100);
        admin.setStatus(1);
        userMapper.insert(admin);

        // ---- 资源 ----
        List<Resource> resources = buildResources();
        resources.forEach(resourceMapper::insert);

        // ---- 示例预约（挂在管理员名下，便于管理端查看演示数据）----
        Long demoUserId = admin.getId();
        LocalDateTime now = LocalDateTime.now();
        insertReservation(demoUserId, 1L, now.plusDays(1).toLocalDate().atTime(10, 0),
                now.plusDays(1).toLocalDate().atTime(12, 0), 2, 0, null, "小组讨论", Reservation.STATUS_PENDING, now.minusDays(1));
        insertReservation(demoUserId, 3L, now.minusDays(1).toLocalDate().atTime(14, 0),
                now.minusDays(1).toLocalDate().atTime(17, 0), 2, 1,
                now.minusDays(1).toLocalDate().atTime(13, 55), "自习", Reservation.STATUS_COMPLETED, now.minusDays(2));
        insertReservation(demoUserId, 4L, now.minusDays(3).toLocalDate().atTime(16, 0),
                now.minusDays(3).toLocalDate().atTime(18, 0), 2, 1,
                now.minusDays(3).toLocalDate().atTime(15, 58), "羽毛球活动", Reservation.STATUS_COMPLETED, now.minusDays(4));

        log.info("演示数据初始化完成：1 个用户，{} 个资源，3 条预约", resources.size());
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

    private List<Resource> buildResources() {
        List<Resource> list = new ArrayList<>();
        long id = 0;

        // 教学楼教室：2 栋 × 5 层 × 10 间
        for (String bld : List.of("教学楼A栋", "教学楼B栋")) {
            String prefix = bld.contains("A") ? "A" : "B";
            for (int floor = 1; floor <= 5; floor++) {
                for (int room = 1; room <= 10; room++) {
                    String roomNo = prefix + floor + String.format("%02d", room);
                    Resource r = base(++id, roomNo + " 教室", 1, bld, floor + "F", roomNo,
                            40 + (room % 3) * 10, LocalTime.of(8, 0), LocalTime.of(22, 0), 120, 1);
                    r.setDescription(bld + " " + floor + "层 " + roomNo + "，配备多媒体投影设备，适合课程教学与小组讨论。");
                    list.add(r);
                }
            }
        }

        // 实验室：6 个学科 × 10 间
        record Subject(String name, String prefix, String desc) {}
        List<Subject> subjects = List.of(
                new Subject("物理", "P", "物理实验专用，配备力学、电学、光学实验仪器。"),
                new Subject("化学", "C", "化学实验专用，配备通风橱、实验台与安全设施。"),
                new Subject("生物", "B", "生物实验专用，配备显微镜、培养箱与生物安全柜。"),
                new Subject("材料", "M", "材料科学实验室，配备拉伸机、硬度计与金相显微镜。"),
                new Subject("计算机", "CS", "计算机实验室，配备高性能工作站与开发环境。"),
                new Subject("机电", "ME", "机电实验室，配备数控机床、PLC与机电控制设备。"));
        for (int s = 0; s < subjects.size(); s++) {
            Subject subj = subjects.get(s);
            int floor = s + 1;
            for (int room = 1; room <= 10; room++) {
                String roomNo = subj.prefix() + floor + String.format("%02d", room);
                Resource r = base(++id, roomNo + " " + subj.name() + "实验室", 2, "学海楼", floor + "F", roomNo,
                        10, LocalTime.of(8, 0), LocalTime.of(20, 0), 180,
                        (room == 3 && floor == 2) ? 3 : 1);
                r.setDescription("学海楼 " + floor + "层 " + subj.name() + "实验室 " + roomNo + "，" + subj.desc());
                list.add(r);
            }
        }

        // 图书馆：6F / 7F 各 60 个多媒体自习位（座位级资源，单人预约）
        for (String floor : List.of("6F", "7F")) {
            for (int seat = 1; seat <= 60; seat++) {
                String roomNo = floor + "-" + String.format("%02d", seat);
                Resource r = base(++id, roomNo + " 多媒体自习位", 3, "图书馆", floor, roomNo,
                        1, LocalTime.of(8, 0), LocalTime.of(22, 0), 240, 1);
                r.setUnit("人");
                r.setDescription("图书馆 " + floor + " 多媒体自习室 " + String.format("%02d", seat)
                        + " 号座位，配备独立电脑与隔板。");
                list.add(r);
            }
        }

        // 体育馆：6 个场馆（capacity 与 unit 一致，如"乒乓球馆 10 台"）
        String[][] gyms = {
                {"游泳馆", "体育馆A栋", "1F", "GYM-A1", "50", "人", "标准游泳池，8 条泳道，配备更衣室与热水淋浴。"},
                {"乒乓球馆", "体育馆A栋", "2F", "GYM-A2", "10", "台", "10 张标准乒乓球台，适合教学与比赛。"},
                {"篮球场", "体育馆B栋", "1F", "GYM-B1", "30", "人", "标准篮球场，可进行全场或半场比赛。"},
                {"羽毛球场", "体育馆B栋", "2F", "GYM-B2", "4", "片", "4 片标准羽毛球场地，塑胶地面。"},
                {"网球场", "体育馆B栋", "1F", "GYM-B3", "2", "片", "2 片标准网球场，硬地塑胶面层。"},
                {"排球场", "体育馆B栋", "2F", "GYM-B4", "24", "人", "标准排球场，可进行教学与比赛。"}};
        for (String[] g : gyms) {
            Resource r = base(++id, g[0], 4, g[1], g[2], g[3],
                    Integer.parseInt(g[4]), LocalTime.of(9, 0), LocalTime.of(21, 0), 90, 1);
            r.setUnit(g[5]);
            r.setDescription(g[6]);
            list.add(r);
        }
        return list;
    }

    private Resource base(long id, String name, int type, String building, String floor, String roomNo,
                          int capacity, LocalTime open, LocalTime close, int maxDuration, int status) {
        Resource r = new Resource();
        r.setId(id);
        r.setName(name);
        r.setResourceType(type);
        r.setBuilding(building);
        r.setFloor(floor);
        r.setRoomNo(roomNo);
        r.setCapacity(capacity);
        r.setUnit("人");
        // 经纬度：按资源类型对应武汉理工大学各建筑（WGS-84，浏览器定位返回坐标系）
        double[] ll = switch (type) {
            case 1 -> new double[]{30.5125, 114.3390};  // 教室 → 博学主楼（马房山校区）
            case 2 -> new double[]{30.517416, 114.340175};  // 实验室 → 学海楼（马房山校区，实测WiFi定位值）
            case 3 -> new double[]{30.5066, 114.3285};  // 图书馆 → 南湖图书馆（南湖校区）
            case 4 -> new double[]{30.5040, 114.3270};  // 体育馆 → 南湖体育馆（南湖校区）
            default -> new double[]{30.5100, 114.3300};
        };
        // 同建筑内各房间适度散开（±2 米级），均在打卡阈值范围内
        r.setLatitude(BigDecimal.valueOf(ll[0] + (id % 10) * 0.00002).setScale(6, java.math.RoundingMode.HALF_UP));
        r.setLongitude(BigDecimal.valueOf(ll[1] + (id % 10) * 0.00002).setScale(6, java.math.RoundingMode.HALF_UP));
        r.setOpenTime(open);
        r.setCloseTime(close);
        r.setMaxDuration(maxDuration);
        r.setStatus(status);
        return r;
    }
}

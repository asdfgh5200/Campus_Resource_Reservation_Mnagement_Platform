package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.ReservationDTO;
import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.entity.SysUser;
import com.campus.reservation.mapper.ReservationMapper;
import com.campus.reservation.mapper.ResourceMapper;
import com.campus.reservation.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预约创建的真并发集成测试：10 个用户同时抢同一自习位的同一时段。
 * 正确行为：恰好 1 人成功、库中恰好 1 条记录（其余全部 409 冲突）。
 * 若出现 0 个或 ≥2 个成功，说明"查冲突→插入"存在竞态窗口。
 */
@SpringBootTest
class ReservationConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationMapper reservationMapper;
    @Autowired
    private ResourceMapper resourceMapper;
    @Autowired
    private SysUserMapper userMapper;

    @Test
    @DisplayName("并发抢同一时段：10 线程同刻下单 → 仅 1 人成功且库中仅 1 条")
    void concurrentCreate_sameSlot_onlyOneSucceeds() throws Exception {
        // ---- 准备：独立临时资源 + 10 个新用户（避免污染既有数据） ----
        Resource seat = new Resource();
        seat.setName("并发测试自习位");
        seat.setResourceType(3); // 图书馆座位：同时段只允许 1 单
        seat.setCapacity(1);
        seat.setUnit("人");
        seat.setOpenTime(LocalTime.of(8, 0));
        seat.setCloseTime(LocalTime.of(22, 0));
        seat.setMaxDuration(240);
        seat.setStatus(1);
        resourceMapper.insert(seat);

        long ts = System.nanoTime();
        List<SysUser> users = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            SysUser u = new SysUser();
            u.setUsername("conc_" + ts + "_" + i);
            u.setPassword("x");
            u.setNickname("并发用户" + i);
            u.setRole(0);
            u.setCreditScore(100);
            u.setStatus(1);
            userMapper.insert(u);
            users.add(u);
        }

        LocalDateTime day = LocalDate.now().plusDays(2).atStartOfDay();
        LocalDateTime start = day.plusHours(10);
        LocalDateTime end = day.plusHours(11);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        try {
            for (SysUser u : users) {
                futures.add(pool.submit(() -> {
                    UserContext.set(u.getId(), 0); // ThreadLocal：每个工作线程各自登录态
                    ready.countDown();
                    try {
                        go.await(10, TimeUnit.SECONDS); // 所有线程就位后同时发令
                        ReservationDTO dto = new ReservationDTO();
                        dto.setResourceId(seat.getId());
                        dto.setStartTime(start);
                        dto.setEndTime(end);
                        dto.setHeadcount(1);
                        reservationService.create(dto);
                        success.incrementAndGet();
                    } catch (BizException e) {
                        if (e.getCode() == 409) {
                            conflict.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // 死锁/锁等待超时等也算并发异常，单独计数便于观察
                        System.err.println("并发异常: " + e);
                    } finally {
                        UserContext.clear();
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
            for (var f : futures) {
                f.get(10, TimeUnit.SECONDS); // 传播任务线程中的断言失败
            }

            Long rows = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                    .eq(Reservation::getResourceId, seat.getId()));

            // ---- 并发正确性的硬指标 ----
            assertThat(success.get())
                    .as("成功下单数（>1 说明超卖：查冲突与插入之间存在竞态窗口）")
                    .isEqualTo(1);
            assertThat(rows)
                    .as("库中实际记录数必须与成功数一致")
                    .isEqualTo(1L);
            assertThat(conflict.get()).isEqualTo(THREADS - 1);
        } finally {
            // ---- 清理临时数据（逻辑删除） ----
            reservationMapper.delete(Wrappers.<Reservation>lambdaQuery()
                    .eq(Reservation::getResourceId, seat.getId()));
            users.forEach(u -> userMapper.deleteById(u.getId()));
            resourceMapper.deleteById(seat.getId());
        }
    }
}

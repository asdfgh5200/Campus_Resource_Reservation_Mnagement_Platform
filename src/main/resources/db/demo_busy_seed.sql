-- ============================================================
-- 演示数据脚本①：构造「今天·当前时段」约 60% 资源正在被使用
-- 效果：随机挑选 60% 的实验室/图书馆/体育馆资源，为其生成一条
--       status=2(使用中) 的预约，时段横跨当前时刻，人数约为容量的 60%（不占满）。
-- 所有模拟数据均带 [DEMO:busy] 标记，预约人使用独立的 demo_uXX 账号，
-- 可随 demo_busy_revoke.sql 一键撤销；本脚本开头也会自动清理旧批次（可重复执行）。
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 campus_reservation ^
--         -e "source D:/github/Campus_Resource_Reservation_Mnagement_Platform/src/main/resources/db/demo_busy_seed.sql"
-- 建议：在资源开放时段内运行（如 9:00–20:00）；深夜运行时多数资源窗口无效，生成数会偏少。
-- ============================================================

-- 0) 清理旧的模拟数据（幂等，防止重复执行叠加）
DELETE FROM reservation_log WHERE detail LIKE '[DEMO:%';
DELETE FROM reservation WHERE remark LIKE '[DEMO:%';

-- 1) 补齐 demo 用户池：demo_u01..demo_u40（密码复制 admin 的哈希，仅用于挂数据，不参与登录演示）
INSERT IGNORE INTO sys_user (username, password, nickname, role, status)
SELECT CONCAT('demo_u', LPAD(d.n, 2, '0')), p.pwd, CONCAT('模拟用户', LPAD(d.n, 2, '0')), 0, 1
FROM (
    SELECT ones.d + tens.d * 10 + 1 AS n
    FROM (SELECT 0 AS d UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) ones,
         (SELECT 0 AS d UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) tens
) d
CROSS JOIN (
    SELECT COALESCE(
        (SELECT password FROM (SELECT password FROM sys_user WHERE username = 'admin' LIMIT 1) t1),
        (SELECT password FROM (SELECT password FROM sys_user ORDER BY id LIMIT 1) t2)
    ) AS pwd
) p
WHERE d.n <= 40;

-- 2) 生成「使用中」预约：候选窗口 = [max(开放,现在-1h), min(关闭,现在+1h)]，随机取 60%
INSERT INTO reservation
    (user_id, resource_id, start_time, end_time, headcount, checkin_flag, checkin_time, status, remark)
SELECT u.id, x.resource_id, x.start_time, x.end_time, x.headcount,
       1, DATE_SUB(x.start_time, INTERVAL 5 MINUTE), 2, '[DEMO:busy]'
FROM (
    SELECT c.*,
           ROW_NUMBER() OVER (ORDER BY RAND()) AS rn,
           COUNT(*)     OVER ()                AS total
    FROM (
        SELECT r.id                                                          AS resource_id,
               GREATEST(TIMESTAMP(CURDATE(), r.open_time),
                        DATE_SUB(NOW(), INTERVAL 60 MINUTE))                 AS start_time,
               LEAST(TIMESTAMP(CURDATE(), r.close_time),
                     DATE_ADD(NOW(), INTERVAL 60 MINUTE))                    AS end_time,
               GREATEST(1, FLOOR(r.capacity * (0.55 + RAND() * 0.1)))        AS headcount
        FROM resource r
        WHERE r.deleted = 0 AND r.status = 1 AND r.resource_type IN (2, 3, 4)
          -- 该资源此刻仍在使用窗口内
          AND TIMESTAMP(CURDATE(), r.open_time)  < DATE_ADD(NOW(), INTERVAL 60 MINUTE)
          AND TIMESTAMP(CURDATE(), r.close_time) > DATE_SUB(NOW(), INTERVAL 60 MINUTE)
          -- 不与任何真实/进行中的预约冲突（只做排除，真实数据原样保留）
          AND NOT EXISTS (
              SELECT 1 FROM reservation b
              WHERE b.deleted = 0 AND b.resource_id = r.id AND b.status IN (1, 2)
                AND b.start_time < LEAST(TIMESTAMP(CURDATE(), r.close_time),
                                         DATE_ADD(NOW(), INTERVAL 60 MINUTE))
                AND b.end_time   > GREATEST(TIMESTAMP(CURDATE(), r.open_time),
                                            DATE_SUB(NOW(), INTERVAL 60 MINUTE))
          )
    ) c
    WHERE c.start_time < NOW() AND c.end_time > NOW()
      AND TIMESTAMPDIFF(MINUTE, c.start_time, c.end_time) >= 30
) x
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY username) AS rn
    FROM sys_user
    WHERE username LIKE 'demo\_u%' AND role = 0 AND status = 1 AND deleted = 0
) u ON u.rn = x.rn
WHERE x.rn <= CEIL(x.total * 0.6);

-- 3) 配套操作日志（让管理端日志/概览看起来真实：创建 + 打卡各一条）
INSERT INTO reservation_log (user_id, username, nickname, reservation_id, resource_id, resource_name, action, detail)
SELECT rv.user_id, su.username, su.nickname, rv.id, rv.resource_id, r.name, 1, '[DEMO:busy] 模拟创建预约'
FROM reservation rv
JOIN sys_user su ON su.id = rv.user_id
JOIN resource r  ON r.id  = rv.resource_id
WHERE rv.remark = '[DEMO:busy]' AND rv.deleted = 0;

INSERT INTO reservation_log (user_id, username, nickname, reservation_id, resource_id, resource_name, action, detail)
SELECT rv.user_id, su.username, su.nickname, rv.id, rv.resource_id, r.name, 3, '[DEMO:busy] 模拟验证码打卡'
FROM reservation rv
JOIN sys_user su ON su.id = rv.user_id
JOIN resource r  ON r.id  = rv.resource_id
WHERE rv.remark = '[DEMO:busy]' AND rv.deleted = 0;

-- 4) 结果汇总
SELECT COUNT(*)      AS 模拟使用中预约数,
       SUM(headcount) AS 总占用人次
FROM reservation WHERE remark = '[DEMO:busy]' AND deleted = 0;

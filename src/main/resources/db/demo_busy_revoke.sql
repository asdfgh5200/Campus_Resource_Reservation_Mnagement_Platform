-- ============================================================
-- 演示数据脚本②：一键撤销 demo_busy_seed.sql 生成的全部模拟数据
-- 删除范围（仅限带标记的数据，真实用户数据不受任何影响）：
--   1) remark 以 [DEMO: 开头的预约（占用状态随之自动恢复，因为占用由预约表实时推导）
--   2) detail 以 [DEMO: 开头的操作日志
--   3) demo_u01..demo_u40 模拟账号
-- 可重复执行（无匹配时为无害的空操作）。
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 campus_reservation ^
--         -e "source D:/github/Campus_Resource_Reservation_Mnagement_Platform/src/main/resources/db/demo_busy_revoke.sql"
-- ============================================================

DELETE FROM reservation_log WHERE detail LIKE '[DEMO:%';
DELETE FROM reservation     WHERE remark LIKE '[DEMO:%';
DELETE FROM sys_user        WHERE username LIKE 'demo\_u%';

-- 撤销结果确认（应全部为 0）
SELECT
    (SELECT COUNT(*) FROM reservation     WHERE remark LIKE '[DEMO:%' AND deleted = 0) AS 残留模拟预约,
    (SELECT COUNT(*) FROM reservation_log WHERE detail LIKE '[DEMO:%')                 AS 残留模拟日志,
    (SELECT COUNT(*) FROM sys_user        WHERE username LIKE 'demo\_u%')               AS 残留模拟账号;

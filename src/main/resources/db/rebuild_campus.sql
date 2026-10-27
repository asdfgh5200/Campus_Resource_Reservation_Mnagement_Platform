-- 全校真实资源重建脚本
--
-- 为什么要整表重建而不是 TRUNCATE：
--   旧库的 resource 表没有 campus 列，而 schema.sql 用的是 CREATE TABLE IF NOT EXISTS，
--   已存在的表不会自动补列；同时资源与课表 ID 已全部换新，旧的预约/课表记录无法对应。
--
-- 重建后的数据规模（由 DataInitializer 在空库时自动播种）：
--   · 资源 1463 个 = 教室 820 + 实验室 155 + 体育馆 368 + 图书馆座位 120
--   · 覆盖 4 个校区（南湖 / 马房山 / 余家头 / 军山）、53 栋楼
--   · 课表 10691 条，来自教务系统 2026-2027-1 学期真实排课
--
-- 使用方式：
--   1. 在 IDEA 数据库控制台（campus_reservation@localhost）执行本脚本；
--   2. 重启后端：schema.sql 按新结构建表，DataInitializer 检测到空库后自动播种；
--   3. 启动日志出现「演示数据初始化完成」后，用 admin / admin 登录验证。
--
-- 注意：本脚本会删除全部业务表，仅在需要重建种子时使用。

USE `campus_reservation`;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user_message`;
DROP TABLE IF EXISTS `campus_closure`;
DROP TABLE IF EXISTS `system_config`;
DROP TABLE IF EXISTS `reservation_log`;
DROP TABLE IF EXISTS `holiday`;
DROP TABLE IF EXISTS `repair_order`;
DROP TABLE IF EXISTS `violation`;
DROP TABLE IF EXISTS `checkin_code`;
DROP TABLE IF EXISTS `course_schedule`;
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `resource`;
DROP TABLE IF EXISTS `sys_user`;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '已清空全部业务表，请重启后端以按新结构建表并播种全校真实资源' AS message;

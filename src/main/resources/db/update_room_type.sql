-- ============================================================
-- 【可选脚本】刷新资源细分类型（不删除任何表，不影响用户/预约/违规数据）
--
-- 正常情况下不需要执行本脚本：后端启动时 DataInitializer#syncRoomTypes 会自动比对
-- campus_rooms.csv 的第 8 列并回填 room_type，重启即可生效（启动日志会打印同步条数）。
-- 本脚本仅供无法重启、或想直接看 SQL 效果时手动执行。
--
-- 教室   room_type：多媒体教室 / 普通教室 / 学院专用 / 机房 / 制图教室 / 自习教室
-- 实验室 room_type：按楼栋归属的学科方向（化学 / 化学生物 / 工程实训 / 交通运输 / 管理 / 通用实验）
-- 体育馆 room_type：按场地名解析的运动类型（篮球 / 网球 / 田径 ...）
-- 注意：教室（resource_type=1）的类型来自教务系统原字段，无法用 SQL 规则推导，
--      需依靠启动同步回填；若只执行本脚本，教室类型仍为空。
-- ============================================================

-- 1) 实验室：按楼栋归属学科方向
UPDATE resource SET room_type = CASE
        WHEN building = '化学楼' THEN '化学'
        WHEN building = '化生楼' THEN '化学生物'
        WHEN building IN ('工科实训中心3号楼', '东院工程训练中心', '余区工程训练中心') THEN '工程实训'
        WHEN building = '交运楼' THEN '交通运输'
        WHEN building = '管理学院楼' THEN '管理'
        ELSE '通用实验'
    END
WHERE resource_type = 2;

-- 2) 体育馆：按场地名解析运动类型（CASE 按顺序求值，先长词后短词）
UPDATE resource SET room_type = CASE
        WHEN name LIKE '%气排球%' THEN '气排球'
        WHEN name LIKE '%乒乓球%' THEN '乒乓球'
        WHEN name LIKE '%羽毛球%' THEN '羽毛球'
        WHEN name LIKE '%跆拳道%' THEN '跆拳道'
        WHEN name LIKE '%柔力球%' THEN '柔力球'
        WHEN name LIKE '%拉丁舞%' THEN '舞蹈'
        WHEN name LIKE '%健美%' THEN '健美操'
        WHEN name LIKE '%体侧%' THEN '体质测试'
        WHEN name LIKE '%体测%' THEN '体质测试'
        WHEN name LIKE '%篮球%' THEN '篮球'
        WHEN name LIKE '%足球%' THEN '足球'
        WHEN name LIKE '%排球%' THEN '排球'
        WHEN name LIKE '%网球%' THEN '网球'
        WHEN name LIKE '%游泳%' THEN '游泳'
        WHEN name LIKE '%田径%' THEN '田径'
        WHEN name LIKE '%瑜伽%' THEN '瑜伽'
        WHEN name LIKE '%舞蹈%' THEN '舞蹈'
        WHEN name LIKE '%散打%' THEN '散打'
        WHEN name LIKE '%体能%' THEN '体能训练'
        WHEN name LIKE '%健身%' THEN '健身'
        WHEN name LIKE '%器材%' THEN '器材'
        WHEN name LIKE '%体操%' THEN '体操'
        WHEN name LIKE '%武术%' THEN '武术'
        ELSE '综合场地'
    END
WHERE resource_type = 4;

-- 3) 校验：教室 820 / 实验室 155 / 体育馆 368 / 图书馆 120
SELECT resource_type, room_type, COUNT(*) AS cnt
FROM resource
GROUP BY resource_type, room_type
ORDER BY resource_type, cnt DESC;

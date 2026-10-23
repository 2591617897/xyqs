-- ============================================================
-- 迁移：学校所属区域字段（省/市/区县）
-- 背景：市域帖子流（切换「本校 / 本市」）按 t_school.city 聚合，
--       区县用于「当前位置」展示与定位兜底。
-- 执行：mysql -u root -p guizhou_campus_fun < migration-20261024-school-district.sql
-- ============================================================
USE guizhou_campus_fun;

-- 1) 学校表补区域字段（province/city 建表时已有，此处仅补 district；幂等）
SET @has_district := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_school' AND COLUMN_NAME = 'district'
);
SET @ddl := IF(@has_district = 0,
  'ALTER TABLE t_school ADD COLUMN district VARCHAR(32) DEFAULT NULL COMMENT ''所属区县（区域定位展示）'' AFTER city',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- city 上补索引（市域聚合按 city 查学校 id 列表）
SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_school' AND INDEX_NAME = 'idx_city'
);
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE t_school ADD KEY idx_city (city)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 种子学校补齐省/市/区县
INSERT INTO t_school (id, name, province, city, district, sort) VALUES
(1001, '清华大学', '北京', '北京', '海淀区', 100),
(1002, '北京大学', '北京', '北京', '海淀区', 99),
(1003, '复旦大学', '上海', '上海', '杨浦区', 98),
(1004, '上海交通大学', '上海', '上海', '闵行区', 97),
(1005, '浙江大学', '浙江', '杭州', '西湖区', 96),
(1006, '武汉大学', '湖北', '武汉', '武昌区', 95),
(1007, '四川大学', '四川', '成都', '武侯区', 94),
(1008, '中山大学', '广东', '广州', '海珠区', 93),
(1009, '华中科技大学', '湖北', '武汉', '洪山区', 92),
(1010, '南京大学', '江苏', '南京', '鼓楼区', 91),
(1011, '西安交通大学', '陕西', '西安', '碑林区', 90),
(1012, '哈尔滨工业大学', '黑龙江', '哈尔滨', '南岗区', 89),
(1013, '山东大学', '山东', '济南', '历城区', 88),
(1014, '厦门大学', '福建', '厦门', '思明区', 87),
(1015, '湖南大学', '湖南', '长沙', '岳麓区', 86),
(1016, '郑州大学', '河南', '郑州', '高新区', 85),
(1017, '深圳大学', '广东', '深圳', '南山区', 84),
(1018, '苏州大学', '江苏', '苏州', '姑苏区', 83),
(1019, '西南大学', '重庆', '重庆', '北碚区', 82),
(1020, '兰州大学', '甘肃', '兰州', '城关区', 81)
ON DUPLICATE KEY UPDATE province = VALUES(province), city = VALUES(city), district = VALUES(district);

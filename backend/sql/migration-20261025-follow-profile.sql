-- ============================================================
-- 迁移：关注关系表 t_follow + t_user 关注/粉丝计数字段
-- 背景：关注/粉丝与公开主页（campus_follow_profile）。
--       t_follow 与 t_user 同库（guizhou_campus_fun），由 campus-user(8102) 维护：
--       关注/取消在同一事务内写关系行并更新两行计数器。
-- 执行：mysql -u root -p guizhou_campus_fun < migration-20261025-follow-profile.sql
-- 幂等：可重复执行——
--       t_follow 用 CREATE TABLE IF NOT EXISTS；
--       t_user 两列用 information_schema 判断，已存在则跳过 ALTER。
-- ============================================================
USE guizhou_campus_fun;

-- 1) 关注关系表
--    uk_pair(follower_id, followee_id)：幂等兜底，并发双插时后到者按「已关注」处理；
--    idx_followee / idx_follower：粉丝/关注列表与计数查询。
CREATE TABLE IF NOT EXISTS t_follow (
  id          BIGINT      NOT NULL PRIMARY KEY COMMENT '雪花ID',
  follower_id BIGINT      NOT NULL COMMENT '关注者（谁发起关注）',
  followee_id BIGINT      NOT NULL COMMENT '被关注者',
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  UNIQUE KEY uk_pair (follower_id, followee_id),
  KEY idx_followee (followee_id, created_at),
  KEY idx_follower (follower_id, created_at)
) COMMENT '关注关系';

-- 2) t_user 补粉丝数字段（幂等 ALTER，列已存在则跳过）
SET @has_fans := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'fans_count'
);
SET @ddl := IF(@has_fans = 0,
  'ALTER TABLE t_user ADD COLUMN fans_count INT NOT NULL DEFAULT 0 COMMENT ''粉丝数'' AFTER bio',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) t_user 补关注数字段（幂等 ALTER，列已存在则跳过）
SET @has_follow := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'follow_count'
);
SET @ddl := IF(@has_follow = 0,
  'ALTER TABLE t_user ADD COLUMN follow_count INT NOT NULL DEFAULT 0 COMMENT ''关注数'' AFTER fans_count',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

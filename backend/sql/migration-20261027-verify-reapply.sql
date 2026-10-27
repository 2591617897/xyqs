-- ============================================================
-- 学籍重认证（2026-10-27）
--   t_user.verify_submit_at：最近一次提交认证时间，只用于重认证冷却判断。
--   不复用 updated_at —— 改昵称/换头像都会刷新它，冷却会被意外重置。
-- ============================================================

ALTER TABLE t_user
  ADD COLUMN verify_submit_at datetime NULL COMMENT '最近一次提交认证时间（重认证冷却判断）';

-- 回填历史数据：已提交过认证的用户用 updated_at 近似。
-- 不回填的话这些用户 verify_submit_at 为 NULL，首次重认证会被直接放行（等于无冷却）。
UPDATE t_user
   SET verify_submit_at = updated_at
 WHERE verify_status IN (1, 2, 3)
   AND verify_submit_at IS NULL;

-- 回滚：
-- ALTER TABLE t_user DROP COLUMN verify_submit_at;

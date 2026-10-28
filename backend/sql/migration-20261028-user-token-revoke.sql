-- ============================================================================
-- C 端令牌吊销 + 封禁即时生效（2026-10-28）
--
-- 背景（这是第三轮安全审计的 P0-1）：
--   1. 后台「封禁」只改 t_user.status，而内容服务（goods/wall/post）**一次都不查 status**，
--      被封禁的人用旧令牌照样发帖；
--   2. JWT 无状态，封禁改库对已签发令牌没有任何影响，有效期 72 小时；
--   3. 更糟的是 loginOrCreate 也不判 status、UserBrief 连 status 字段都没有，
--      被封禁的人重开小程序 → wx.login → 直接换到一张全新的 72 小时令牌。
--   结果：封禁按钮只有心理作用。
--
-- 方案（与 t_admin_user 的 token_version 完全同一套思路，不引 Redis、不维护黑名单）：
--   给 t_user 加 token_version，签发令牌时写进 claim tv；网关每次校验令牌后
--   比对库里的值（60 秒缓存），不一致或 status=0 就 401。
--   · 封禁 / 解封 / 注销 / 恢复 → token_version + 1，旧令牌 60 秒内全部失效
--   · 存量令牌没有 tv claim，按 0 处理；库里默认值也是 0，所以**升级不会把所有人踢下线**
--
-- 幂等：用 information_schema 判断后再执行，可重复跑。
-- 执行顺序：先跑本脚本，再重启 campus-user / campus-gateway。
-- ============================================================================
USE guizhou_campus_fun;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'guizhou_campus_fun'
    AND TABLE_NAME = 't_user'
    AND COLUMN_NAME = 'token_version'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE t_user ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT ''令牌版本：封禁/解封/注销/恢复/改密时 +1，旧令牌立即失效''',
  'SELECT ''token_version 已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------
-- SHOW COLUMNS FROM t_user LIKE 'token_version';
-- 存量用户全部是 0，与「令牌里没有 tv 就按 0 算」对齐，不会造成大规模掉线：
-- SELECT COUNT(*) AS total, SUM(token_version = 0) AS v0 FROM t_user;

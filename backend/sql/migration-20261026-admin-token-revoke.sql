-- ============================================================================
-- 后台令牌吊销迁移（2026-10-26 续）
--
-- 背景：JWT 是无状态的，令牌签出去就收不回。改密、重置密码、禁用账号之后，
--       对方手里的旧令牌在有效期内（默认 8 小时）仍然能继续调接口 ——
--       这是「改完密码，别处的登录还在」的直接原因。
--
-- 方案：给 t_admin_user 加一个 token_version，签发令牌时写进 claim tv，
--       每次请求与库里的值比对；改密 / 重置 / 禁用 / 删除时把库里的值 +1，
--       所有旧令牌当场失效（无需 Redis，无需令牌黑名单）。
--
-- 幂等：用 information_schema 判断后再执行，可重复跑。
-- 执行顺序：先跑本脚本，再重启 campus-admin（campus-admin 启动时会 select 该列）。
-- ============================================================================
USE guizhou_campus_fun;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'guizhou_campus_fun'
    AND TABLE_NAME = 't_admin_user'
    AND COLUMN_NAME = 'token_version'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE t_admin_user ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT ''令牌版本：改密/重置/禁用/删除时 +1，旧令牌立即失效'' AFTER must_change_pwd',
  'SELECT ''token_version 已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------
-- 应看到 token_version 列，且默认值 0
-- SHOW COLUMNS FROM t_admin_user LIKE 'token_version';
-- 现网存量账号（含 admin）在下次登录时拿到的是当前版本的令牌，无需额外处理
-- SELECT id, username, token_version, must_change_pwd, status FROM t_admin_user;

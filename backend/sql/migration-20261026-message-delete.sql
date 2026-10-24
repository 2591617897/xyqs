-- ============================================================
-- 迁移：私信左滑删除所需的数据结构
-- 背景：消息中心/聊天页支持左滑删除，需要两套「删除」语义：
--   1) 删除会话：只对当前用户生效（对方会话不受影响）→ 用隐藏表记录
--   2) 删除单条消息：软删除（撤回），发送者本人可删，双方都不可见
-- 执行：mysql -u root -p guizhou_campus_fun < migration-20261026-message-delete.sql
-- 幂等：可重复执行——
--       隐藏表用 CREATE TABLE IF NOT EXISTS；
--       t_message.deleted 列用 information_schema 判断，已存在则跳过 ALTER。
-- ============================================================
USE guizhou_campus_fun;

-- 1) 会话隐藏表（用户维度）
--    uk_user_conv 保证「同一用户对同一会话只隐藏一次」，删除接口可重复调用。
CREATE TABLE IF NOT EXISTS t_message_conversation_hide (
  id              BIGINT      NOT NULL PRIMARY KEY COMMENT '雪花ID',
  user_id         BIGINT      NOT NULL COMMENT '执行删除的用户（只有他自己看不到该会话）',
  conversation_id BIGINT      NOT NULL COMMENT '被隐藏的会话 id',
  created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '隐藏时间',
  UNIQUE KEY uk_user_conv (user_id, conversation_id),
  KEY idx_conv (conversation_id)
) COMMENT '会话隐藏记录（用户维度删除会话）';

-- 2) t_message 补软删除字段（幂等 ALTER，列已存在则跳过）
SET @has_deleted := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_message' AND COLUMN_NAME = 'deleted'
);
SET @ddl := IF(@has_deleted = 0,
  'ALTER TABLE t_message ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''0正常 1已删除（撤回）'' AFTER read_flag',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

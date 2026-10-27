-- ============================================================================
-- 群聊「引用回复」与「2 分钟内撤回」（2026-10-27）
--
-- 背景：
--   校友群原来只能发纯文本，长按消息没有任何动作；撤回只有后台管理员能做。
--   现在补三件事：
--     1) 引用回复：消息可以指向另一条消息（微信式「回复」），
--        被引用内容存**快照**，因为原消息可能被撤回或 12 个月后归档。
--     2) 用户自助撤回：发送后 2 分钟内可撤回（与微信一致），软删同 deleted 列。
--     3) deleted_by：区分「本人撤回」和「管理员删除」，
--        前端文案不同（「你撤回了一条消息」vs「该消息已被删除」），
--        只靠 deleted=1 一个 bit 是分不出来的。
--
-- 设计要点：
--   1) reply_to_name / reply_to_text 是快照，不做 JOIN：
--      群消息一页 30 条，若每条都 JOIN 原消息，翻历史时会连锁扫描。
--   2) reply_to_id 只用于「点击引用跳转到原消息」，原消息不在本地列表时就只显示文字。
--   3) 归档表同步加列——它自称「结构与热表一致」，
--      漏掉的话 12 个月前的引用回复会在归档后丢掉上下文。
--
-- ⚠️ 本脚本是**幂等**的：列已存在会跳过，可反复执行。
--    必须先跑本脚本再重启 campus-message，否则实体带新字段后
--    select t_group_message 会 Unknown column 'reply_to_id'，群聊直接不可用。
--
-- 执行：mysql -uroot -p -D <库名> < migration-20261027-group-reply-recall.sql
-- ============================================================================
SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1) t_group_message：引用回复 + 删除来源
-- ------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message' AND COLUMN_NAME = 'reply_to_id') = 0,
    'ALTER TABLE t_group_message ADD COLUMN reply_to_id BIGINT DEFAULT NULL COMMENT ''被回复的消息 id，NULL = 不是回复'' AFTER at_user_ids',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message' AND COLUMN_NAME = 'reply_to_name') = 0,
    'ALTER TABLE t_group_message ADD COLUMN reply_to_name VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''被回复者昵称快照'' AFTER reply_to_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message' AND COLUMN_NAME = 'reply_to_text') = 0,
    'ALTER TABLE t_group_message ADD COLUMN reply_to_text VARCHAR(140) NOT NULL DEFAULT '''' COMMENT ''被回复内容摘要快照（原消息撤回后仍能看懂）'' AFTER reply_to_name',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message' AND COLUMN_NAME = 'deleted_by') = 0,
    'ALTER TABLE t_group_message ADD COLUMN deleted_by TINYINT NOT NULL DEFAULT 0 COMMENT ''删除来源 0未删除 1本人撤回 2管理员删除'' AFTER deleted',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2) t_group_message_archive：保持与热表结构一致
-- ------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message_archive' AND COLUMN_NAME = 'reply_to_id') = 0,
    'ALTER TABLE t_group_message_archive ADD COLUMN reply_to_id BIGINT DEFAULT NULL AFTER at_user_ids',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message_archive' AND COLUMN_NAME = 'reply_to_name') = 0,
    'ALTER TABLE t_group_message_archive ADD COLUMN reply_to_name VARCHAR(32) NOT NULL DEFAULT '''' AFTER reply_to_id',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message_archive' AND COLUMN_NAME = 'reply_to_text') = 0,
    'ALTER TABLE t_group_message_archive ADD COLUMN reply_to_text VARCHAR(140) NOT NULL DEFAULT '''' AFTER reply_to_name',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_group_message_archive' AND COLUMN_NAME = 'deleted_by') = 0,
    'ALTER TABLE t_group_message_archive ADD COLUMN deleted_by TINYINT NOT NULL DEFAULT 0 AFTER deleted',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3) 历史数据回填：已软删的老数据都是管理员删的（用户撤回功能今天才有）
-- ------------------------------------------------------------
UPDATE t_group_message SET deleted_by = 2 WHERE deleted = 1 AND deleted_by = 0;

-- ------------------------------------------------------------
-- 4) 自检：t_group_message 应输出 4 列，归档表同 4 列
-- ------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = @db
   AND TABLE_NAME IN ('t_group_message','t_group_message_archive')
   AND COLUMN_NAME IN ('reply_to_id','reply_to_name','reply_to_text','deleted_by')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;

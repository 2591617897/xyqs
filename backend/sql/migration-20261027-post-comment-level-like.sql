-- ---------------------------------------------------------------------------
-- 频道帖子评论：等级徽章 / 省份 / 评论点赞（2026-10-27）
--
-- 背景：帖子详情页评论区按参考图 1:1 还原，需要展示
--       ① 评论者 LV 等级徽章（LV 由「发帖数 + 评论数」算出）
--       ② 评论者所在省份（取学校所在省）
--       ③ 每条评论的获赞数与「我是否点过赞」
--
-- 这三个字段全部**实时聚合**，不落冗余列：
--   · 等级   → t_post / t_post_comment 计数
--   · 省份   → t_user.school_id → t_school.province
--   · 点赞数 → t_like(target_type='COMMENT') 聚合
-- 所以本脚本只需要补一个索引。
--
-- ⚠️ t_post_comment 之前只有 idx_post(post_id)，缺 user_id 索引：
--    等级聚合要按 user_id GROUP BY，缺索引会全表扫（评论量大了就是慢查询）。
--
-- 本脚本幂等，可重复执行。
-- ---------------------------------------------------------------------------

USE guizhou_campus_fun;

-- 1) t_post_comment 补 idx_user(user_id)
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_post_comment'
        AND INDEX_NAME = 'idx_user') > 0,
    'SELECT ''skip: t_post_comment.idx_user 已存在''',
    'ALTER TABLE t_post_comment ADD KEY idx_user (user_id)'
  )
);
PREPARE st FROM @sql;
EXECUTE st;
DEALLOCATE PREPARE st;

-- 2) t_post_comment 补 idx_parent(parent_id)
--    楼中楼回复按 parent_id 挂载，组树时按 parent_id 分组需要走索引
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_post_comment'
        AND INDEX_NAME = 'idx_parent') > 0,
    'SELECT ''skip: t_post_comment.idx_parent 已存在''',
    'ALTER TABLE t_post_comment ADD KEY idx_parent (parent_id)'
  )
);
PREPARE st FROM @sql;
EXECUTE st;
DEALLOCATE PREPARE st;

-- 3) t_like 的 target_type 注释补上 COMMENT
--    （仅改注释，不改数据；点赞关系行由业务代码写入）
ALTER TABLE t_like
  MODIFY COLUMN target_type VARCHAR(16) NOT NULL
  COMMENT '目标类型 POST / GOODS / WALL / COMMENT';

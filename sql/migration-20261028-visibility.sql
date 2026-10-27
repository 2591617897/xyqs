-- ============================================================
-- 内容可见范围（2026-10-28）
--
-- 发布闲置 / 帖子时可以选「谁看得见」：
--   visibility = 'school'（默认）：只有本校同学能看到
--   visibility = 'city'          ：同城所有高校的同学都能看到
--
-- ⚠️ 为什么叫 visibility 而不是 scope：
--    列表接口已经有一个 scope 参数，表示「浏览范围」（我以什么身份在逛：本校 / 本市）。
--    可见范围是内容自己的属性，浏览范围是浏览者的视角。两个概念同名会让查询条件
--    写得极其容易出错（尤其 OR 嵌套那段），所以内容字段一律叫 visibility。
--
-- 过滤规则（服务端实现，见 GoodsService#page / PostService#pageInternal）：
--   浏览本校：school_id = 我的学校
--             —— 本校内容无论可见范围如何都能看（本校 ⊂ 本市）
--   浏览本市：school_id IN (同城高校)
--             AND (visibility = 'city' OR school_id = 我的学校)
--             —— 他校内容只有对方主动放开到「同市」才看得到
--
-- 历史数据：ADD COLUMN 带 DEFAULT，已有行自动填 'school'。
--   这是有意的收紧：新功能默认「仅本校」，老内容按同一口径处理，
--   对本校用户没有任何影响（本校内容本校永远可见），只是不再出现在他校的同城流里。
--
-- 索引：不加。现有 idx_school_promote (school_id, status, promote_score, created_at)
--   已覆盖范围查询 + 排序，visibility 是残余过滤，在结果集上过滤代价极小；
--   再建一个 (school_id, status, visibility) 反而可能让优化器选错索引。
--
-- 幂等：MySQL 没有 ADD COLUMN IF NOT EXISTS，重复执行报 duplicate column 可忽略。
-- ============================================================

ALTER TABLE t_goods
  ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'school'
  COMMENT '可见范围 school仅本校 city同市可见';

ALTER TABLE t_post
  ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'school'
  COMMENT '可见范围 school仅本校 city同市可见';

-- ------------------------------------------------------------
-- 兜底：把可能出现的空串 / 空白统一成 school（代码层已校验，这里是数据层保险）
-- ------------------------------------------------------------
UPDATE t_goods SET visibility = 'school' WHERE visibility IS NULL OR TRIM(visibility) = '';
UPDATE t_post  SET visibility = 'school' WHERE visibility IS NULL OR TRIM(visibility) = '';

-- ------------------------------------------------------------
-- 自检：两条都应返回 0 行
-- ------------------------------------------------------------
SELECT 't_goods 非法 visibility' AS chk, COUNT(*) AS bad FROM t_goods
  WHERE visibility NOT IN ('school', 'city')
UNION ALL
SELECT 't_post 非法 visibility', COUNT(*) FROM t_post
  WHERE visibility NOT IN ('school', 'city');

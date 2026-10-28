-- 校园广场：补浏览量 / 需求点赞与评论
-- 2026-10-28
--
-- 背景：首页卡片底栏要统一成「浏览 / 点赞 / 评论 / 分享」四项。
--   帖子与树洞只有 like_count / comment_count，没有 view_count → 底栏左侧永远空白；
--   需求有 view_count / reply_count，但没有点赞和评论。
--
-- ⚠️ 用 mysql 客户端执行时必须带 --default-character-set=utf8mb4，
--    否则中文注释会被按 latin1 解释，落库变成乱码（前面踩过一次）。

SET NAMES utf8mb4;

-- ---------------- 1. 帖子 / 树洞：补浏览量 ----------------
ALTER TABLE t_post
  ADD COLUMN view_count INT NOT NULL DEFAULT 0 COMMENT '浏览量' AFTER comment_count;

ALTER TABLE t_wall_post
  ADD COLUMN view_count INT NOT NULL DEFAULT 0 COMMENT '浏览量' AFTER comment_count;

-- ---------------- 2. 需求：补点赞数 / 评论数 ----------------
ALTER TABLE t_demand
  ADD COLUMN like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数' AFTER view_count;

ALTER TABLE t_demand
  ADD COLUMN comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数' AFTER like_count;

-- ---------------- 3. 需求评论表 ----------------
-- 结构与 t_goods_comment 保持一致（同一套两层树 + 图片 + 楼中楼），
-- 这样前端能直接复用帖子/闲置的评论区样式与交互。
CREATE TABLE IF NOT EXISTS t_demand_comment (
  id           BIGINT       NOT NULL COMMENT '主键',
  demand_id    BIGINT       NOT NULL COMMENT '需求 id',
  user_id      BIGINT       NOT NULL COMMENT '评论者',
  user_name    VARCHAR(64)  DEFAULT NULL COMMENT '昵称快照',
  user_avatar  VARCHAR(255) DEFAULT NULL COMMENT '头像快照',
  content      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '评论内容',
  images       VARCHAR(1000) DEFAULT NULL COMMENT '图片，逗号分隔',
  parent_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '顶层评论为 0，回复为顶层评论 id',
  reply_to_name VARCHAR(64) DEFAULT NULL COMMENT '回复给谁（昵称）',
  like_count   INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_demand (demand_id, created_at),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求评论（含楼中楼回复）';

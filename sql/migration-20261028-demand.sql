-- 校园需求广场（求物 / 求帮忙）
-- 2026-10-28
--
-- ⚠️ 执行时必须带 utf8mb4：mysql --default-character-set=utf8mb4 < 本文件
--    本文件已自带 SET NAMES utf8mb4 兜底 —— 用 Git Bash 默认字符集执行会把中文
--    注释写坏（表能用，但注释变乱码）。
SET NAMES utf8mb4;
--
-- 与闲置（t_goods）完全独立的两张表，不动 t_goods 任何一列。
-- 理由：求购/求助的字段与商品是反的 —— 商品有「成色/保修/入手时间/邮寄方式」，
-- 需求有「要求/预算/期望时间」；状态机也不同（已找到 vs 已售出）。
-- 硬塞进一张表会让每条查询都记得加 flow 过滤，漏一处需求就混进卖货列表。
--
-- 复用的东西：分类白名单（后端常量）、可见范围 visibility、多词搜索 SearchKit、
-- UGC 安全 SecurityGuard、作者快照刷新 UserSnapshotMapper。

CREATE TABLE IF NOT EXISTS t_demand (
  id           BIGINT        NOT NULL PRIMARY KEY,
  user_id      BIGINT        NOT NULL COMMENT '发布者',
  school_id    BIGINT        DEFAULT NULL,
  author_name   VARCHAR(64)  DEFAULT NULL COMMENT '发布者昵称快照(读模型冗余)',
  author_avatar VARCHAR(255) DEFAULT NULL,
  type         TINYINT       NOT NULL DEFAULT 1 COMMENT '1求物 2求帮忙',
  title        VARCHAR(60)   NOT NULL COMMENT '想要什么 / 想做什么',
  description  VARCHAR(2000) DEFAULT NULL COMMENT '具体要求',
  category_id  INT           NOT NULL DEFAULT 0 COMMENT '需求分类, 见 DemandService.DEMAND_CATEGORIES',
  budget       DECIMAL(10,2) DEFAULT NULL COMMENT '预算/报酬; NULL=面议',
  expect_time  VARCHAR(32)   DEFAULT NULL COMMENT '期望时间, 如: 本周内 / 今天下午',
  location     VARCHAR(64)   DEFAULT NULL COMMENT '交接地点, 如: 南苑食堂门口',
  images       VARCHAR(1000) DEFAULT NULL COMMENT '参考图, 逗号分隔',
  visibility   VARCHAR(16)   NOT NULL DEFAULT 'school' COMMENT 'school仅本校 / city同市',
  status       TINYINT       NOT NULL DEFAULT 1 COMMENT '0已下架 1进行中 2已完成',
  view_count   INT           NOT NULL DEFAULT 0,
  reply_count  INT           NOT NULL DEFAULT 0 COMMENT '回应数(含已忽略)',
  created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_school_status (school_id, status, created_at),
  KEY idx_user (user_id, status),
  KEY idx_type_status (type, status, created_at)
) COMMENT '校园需求(求物/求帮忙)';

CREATE TABLE IF NOT EXISTS t_demand_reply (
  id             BIGINT       NOT NULL PRIMARY KEY,
  demand_id      BIGINT       NOT NULL,
  replier_id     BIGINT       NOT NULL COMMENT '回应者',
  replier_name   VARCHAR(64)  DEFAULT NULL COMMENT '回应者昵称快照',
  replier_avatar VARCHAR(255) DEFAULT NULL,
  message        VARCHAR(500) DEFAULT NULL COMMENT '我能提供什么',
  contact        VARCHAR(255) DEFAULT NULL COMMENT '联系方式(QQ/微信/手机), AES 加密存储',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待查看 1已读 2已忽略',
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_demand (demand_id, created_at),
  KEY idx_replier (replier_id, created_at)
) COMMENT '需求回应';

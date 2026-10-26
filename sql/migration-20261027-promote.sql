-- ============================================================
-- 推广功能：看微信激励视频广告 → 换内容排序权重
-- 执行库：guizhou_campus_fun
-- 幂等：MySQL 没有 ADD COLUMN IF NOT EXISTS，重复执行报 duplicate column 可忽略
-- ============================================================

-- ------------------------------------------------------------
-- 1. 三张内容表加推广冗余字段
--
-- promote_score 是「当前有效推广分」，由 campus-promote 定时按 7 天线性衰减重算，
-- 列表排序用它做第一排序键，所以必须落库（不能读时聚合，否则无法走索引排序）。
-- promote_expire_at 是最后一次推广的到期时间：只用于「是否需要重算」的筛选条件，
-- 避免定时任务全表扫描。
-- ------------------------------------------------------------
ALTER TABLE t_goods ADD COLUMN promote_score INT NOT NULL DEFAULT 0 COMMENT '当前有效推广分(7天线性衰减)' AFTER comment_count;
ALTER TABLE t_goods ADD COLUMN promote_expire_at DATETIME DEFAULT NULL COMMENT '推广到期时间' AFTER promote_score;
ALTER TABLE t_goods ADD KEY idx_school_promote (school_id, status, promote_score, created_at);

ALTER TABLE t_post ADD COLUMN promote_score INT NOT NULL DEFAULT 0 COMMENT '当前有效推广分(7天线性衰减)' AFTER status;
ALTER TABLE t_post ADD COLUMN promote_expire_at DATETIME DEFAULT NULL COMMENT '推广到期时间' AFTER promote_score;

ALTER TABLE t_wall_post ADD COLUMN promote_score INT NOT NULL DEFAULT 0 COMMENT '当前有效推广分(7天线性衰减)' AFTER status;
ALTER TABLE t_wall_post ADD COLUMN promote_expire_at DATETIME DEFAULT NULL COMMENT '推广到期时间' AFTER promote_score;

-- ------------------------------------------------------------
-- 2. 推广观看流水（campus-promote 独占）
--
-- 一次「点推广 → 看完广告 → 结算」对应一行。
-- nonce 是一次性凭证：客户端拿到后只能结算一次，唯一键兜住并发重复上报。
-- weight 记的是**衰减前的满分值**（基础分），真实有效分按剩余时间折算，
-- 所以流水本身不随衰减改写，只由定时任务读它重算内容表的 promote_score。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_promote_record (
  id            BIGINT       NOT NULL PRIMARY KEY,
  user_id       BIGINT       NOT NULL COMMENT '推广人(=内容作者)',
  target_type   VARCHAR(16)  NOT NULL COMMENT 'GOODS / POST / WALL',
  target_id     BIGINT       NOT NULL,
  nonce         VARCHAR(48)  NOT NULL COMMENT '一次性凭证，结算后作废',
  weight        INT          NOT NULL DEFAULT 0 COMMENT '本次满分值(衰减前)',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待观看 1已结算 2已作废',
  ad_unit_id    VARCHAR(64)  DEFAULT NULL COMMENT '微信激励视频广告位ID快照',
  issued_at     DATETIME     NOT NULL COMMENT '签发时间，用于校验最短观看时长',
  finish_at     DATETIME     DEFAULT NULL COMMENT '结算时间',
  expire_at     DATETIME     DEFAULT NULL COMMENT '本次权重的到期时间',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_nonce (nonce),
  KEY idx_target (target_type, target_id, status),
  KEY idx_user_time (user_id, created_at),
  KEY idx_expire (status, expire_at)
) COMMENT '推广观看流水';

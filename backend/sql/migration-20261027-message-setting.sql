-- ============================================================
-- 消息提醒设置（2026-10-27）
--
-- 一张表同时管三件事：
--   1. 站内提醒的开关（私信 / 赞与收藏 / 新增粉丝 / 评论与@ / 官方通知）
--   2. 免打扰时段（夜里不弹不推）
--   3. 微信订阅消息的授权状态（后期绑定服务号 / 配好模板后，靠它决定能不能发）
--
-- 读多写少：GET /messages/settings 在没记录时直接返回默认值，不建行；
-- 只有用户真的改过设置才落库（省掉每次登录插一行）。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_message_setting (
  user_id          BIGINT      NOT NULL COMMENT '用户 id，一人一行',
  notify_message   TINYINT     NOT NULL DEFAULT 1 COMMENT '私信提醒 1开 0关',
  notify_like      TINYINT     NOT NULL DEFAULT 1 COMMENT '赞与收藏',
  notify_follow    TINYINT     NOT NULL DEFAULT 1 COMMENT '新增粉丝',
  notify_comment   TINYINT     NOT NULL DEFAULT 1 COMMENT '评论与@',
  notify_system    TINYINT     NOT NULL DEFAULT 1 COMMENT '官方通知',
  dnd_enabled      TINYINT     NOT NULL DEFAULT 0 COMMENT '免打扰开关',
  dnd_start        VARCHAR(5)  NOT NULL DEFAULT '22:00' COMMENT '免打扰开始 HH:mm',
  dnd_end          VARCHAR(5)  NOT NULL DEFAULT '07:00' COMMENT '免打扰结束 HH:mm（可跨天）',
  subscribe_status TINYINT     NOT NULL DEFAULT 0 COMMENT '微信订阅消息 0未授权 1已授权 2已拒绝',
  subscribe_at     DATETIME    DEFAULT NULL COMMENT '最近一次订阅授权时间',
  updated_at       DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) COMMENT '消息提醒设置（站内开关 + 免打扰 + 订阅消息授权状态）';

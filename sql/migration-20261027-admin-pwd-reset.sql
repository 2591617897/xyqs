-- ============================================================
-- 后台邮箱找回密码（2026-10-27）
--
-- 只针对管理后台（t_admin_user / campus-admin），小程序用户没有邮箱字段，
-- 不走这套流程。
--
-- 设计要点：
--   1. 验证码只落哈希（BCrypt），库被读走也拿不到可用验证码；
--   2. 一行一条申请记录，天然支持「60 秒冷却 / 每日上限 / 试错次数」三类限流；
--   3. email 存发送时的快照，管理员事后改绑邮箱不会让已发出的码转移到新地址。
--
-- 幂等：可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_admin_pwd_reset` (
  `id`            bigint       NOT NULL COMMENT '雪花 ID',
  `admin_id`      bigint       NOT NULL COMMENT '申请重置的管理员',
  `email`         varchar(64)  NOT NULL COMMENT '发送目标（发送时快照）',
  `code_hash`     varchar(100) NOT NULL COMMENT '6 位验证码的 BCrypt 哈希',
  `expire_at`     datetime     NOT NULL COMMENT '过期时间',
  `used`          tinyint      NOT NULL DEFAULT 0 COMMENT '1=已用或已作废',
  `attempt_count` tinyint      NOT NULL DEFAULT 0 COMMENT '校验失败次数',
  `req_ip`        varchar(64)  DEFAULT NULL COMMENT '申请来源 IP，用于限流',
  `created_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_reset_admin` (`admin_id`, `used`, `expire_at`),
  KEY `idx_reset_ip` (`req_ip`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台找回密码验证码';

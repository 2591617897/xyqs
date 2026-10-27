-- ============================================================
-- 邮件服务发信日志（2026-10-27）
--
-- t_mail_log 是「这封信到底发出去没有」的唯一事实来源：
--   status = 0 待发 / 1 成功 / 2 失败（失败会由 MQ 重试，重试耗尽进死信队列）
--
-- request_id 唯一索引是消费幂等的支点：MQ 至少要投递一次，重复投递时靠它跳过已成功的消息。
--
-- 幂等：可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_mail_log` (
  `id`          bigint       NOT NULL COMMENT '雪花 ID',
  `request_id`  varchar(32)  NOT NULL COMMENT '消息幂等键',
  `biz_type`    varchar(32)  DEFAULT NULL COMMENT '业务类型，如 ADMIN_RESET_CODE',
  `biz_id`      varchar(64)  DEFAULT NULL COMMENT '业务主键，如管理员 ID',
  `recipient`   varchar(255) NOT NULL COMMENT '收件地址（多个逗号分隔）',
  `template`    varchar(48)  DEFAULT NULL COMMENT '模板编码',
  `subject`     varchar(200) DEFAULT NULL COMMENT '实际发出的邮件标题',
  `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '0待发 1成功 2失败',
  `retry_count` int          NOT NULL DEFAULT 0 COMMENT '已尝试次数（含首次）',
  `error_msg`   varchar(500) DEFAULT NULL COMMENT '最近一次失败原因',
  `sent_at`     datetime     DEFAULT NULL COMMENT '发送成功时间',
  `created_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_request` (`request_id`),
  KEY `idx_mail_biz` (`biz_type`, `biz_id`),
  KEY `idx_mail_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件发送日志';

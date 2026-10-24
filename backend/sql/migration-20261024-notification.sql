-- 2026-10-24 通知中心（点赞 / 关注 / 评论 / @ / 官方）
-- 统一放在 campus-message 服务里，其他服务通过 Feign /internal/notifications 发通知

USE guizhou_campus_fun;

CREATE TABLE IF NOT EXISTS t_notification (
  id              BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
  type            VARCHAR(32)  NOT NULL COMMENT '通知类型：LIKE_GOODS / LIKE_POST / LIKE_WALL / NEW_FOLLOWER / COMMENT_POST / COMMENT_WALL / MENTION / SYSTEM',
  receiver_id     BIGINT       NOT NULL COMMENT '通知接收人',
  actor_id        BIGINT                COMMENT '触发者（官方通知为空）',
  target_type     VARCHAR(32)           COMMENT '关联对象类型：GOODS / POST / WALL / FOCUS',
  target_id       BIGINT                COMMENT '关联对象 id',
  content_snapshot VARCHAR(300)         COMMENT '摘要文本（评论内容前 N 字 / 官方文案）',
  is_read         TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_recipient_read (receiver_id, is_read, created_at),
  KEY idx_recipient_type (receiver_id, type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内通知';

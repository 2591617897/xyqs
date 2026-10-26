-- ============================================================
-- 公告弹窗（进入小程序自动弹出）
--
-- 背景：公告此前只能作为首页的一条静态横条展示，位置固定、容易被忽略。
-- 运营需要「进小程序就弹一次」的强提醒能力，且弹出频次必须由管理员选择：
--   0 不弹窗（仅首页公告条，等于旧行为）
--   1 每次进入都弹（每次冷启动一次，切 tab 不重复）
--   2 每天弹一次
--   3 确认收到后不再弹（点「我知道了」即长期静默）
--
-- 频次状态记在 t_notice_receipt，粒度 = 用户 × 公告：
--   last_shown_at 用于「每天一次」判定，confirmed_at 用于「确认收到」判定。
-- 游客没有 user_id，由小程序本地存储兜底（见 miniprogram/utils/notice.js），
-- 所以这张表只承载登录用户的回执。
-- ============================================================

ALTER TABLE t_admin_notice
  ADD COLUMN popup_mode TINYINT NOT NULL DEFAULT 0
  COMMENT '小程序弹窗：0不弹窗 1每次进入 2每天一次 3确认收到后不再显示'
  AFTER level;

CREATE TABLE IF NOT EXISTS t_notice_receipt (
  id             BIGINT   NOT NULL PRIMARY KEY COMMENT '雪花ID',
  user_id        BIGINT   NOT NULL COMMENT '小程序用户ID',
  notice_id      BIGINT   NOT NULL COMMENT '公告ID',
  shown_count    INT      NOT NULL DEFAULT 0 COMMENT '累计弹出次数',
  first_shown_at DATETIME DEFAULT NULL COMMENT '首次弹出时间',
  last_shown_at  DATETIME DEFAULT NULL COMMENT '最近一次弹出时间（「每天一次」按它判定）',
  confirmed_at   DATETIME DEFAULT NULL COMMENT '确认收到时间（「确认后不再弹」按它判定）',
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_notice (user_id, notice_id),
  KEY idx_notice_confirmed (notice_id, confirmed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '公告弹窗送达与确认回执';

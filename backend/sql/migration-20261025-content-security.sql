-- 2026-10-25 内容安全补全（私信文本审核 + 图片异步审核台账）
--
-- 背景：文本审核（msg_sec_check v2）此前已接入发帖/表白墙/闲置/评论/昵称，
-- 本次补齐两处空白：
--   1) 私信（campus-message）没有任何内容审核；
--   2) 图片（帖子图、闲置图、评论配图、私信图）完全没送审。
--
-- 图片为什么需要一张表：微信 media_check_async 只返回 trace_id，
-- 结论要等「消息推送」回调才回来。没有台账，回调时无法定位这张图属于哪条内容。
-- 违规结论另外写 t_content_audit（source=2 系统自动），复用后台已有的审核记录页面。

USE guizhou_campus_fun;

CREATE TABLE IF NOT EXISTS t_media_check (
  id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键（雪花）',
  trace_id    VARCHAR(80)  NOT NULL COMMENT '微信返回的 trace_id，回调据此反查',
  user_id     BIGINT                COMMENT '提交者',
  biz_type    VARCHAR(20)           COMMENT '业务类型：POST / WALL / GOODS / P_COMMENT / W_COMMENT / G_COMMENT / MESSAGE',
  biz_id      BIGINT                COMMENT '业务主键；发布前提交不到 id 时为 NULL',
  media_url   VARCHAR(500) NOT NULL COMMENT '送审的图片地址（公网 http/https）',
  media_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=图片 2=音频',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待审 1=通过 2=疑似 3=违规 4=送审失败',
  suggest     VARCHAR(20)           COMMENT '微信 result.suggest：pass / review / risky',
  label       INT                   COMMENT '微信 result.label，违规类型编码',
  errcode     INT                   COMMENT '微信 errcode，非 0 表示失败',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_trace (trace_id),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_status (status, created_at),
  KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='图片/音频内容安全送审台账';

-- t_content_audit 的 source 增加取值 2（系统自动拦截），无需改表结构：
-- 原定义 source TINYINT DEFAULT 1 COMMENT '1后台人工 2系统自动（敏感词） 3举报处理'，
-- 内容安全的自动拦截同样落在 2，action 用 REJECT（拒绝发布）/ WARN（疑似放行待复核），
-- target_id=0 表示拦截时内容尚未落库。
-- 这里只补一条索引，方便后台按「系统自动拦截」快速筛选。
SET @idx_exists := (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = 'guizhou_campus_fun'
    AND table_name = 't_content_audit'
    AND index_name = 'idx_source_created'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE t_content_audit ADD KEY idx_source_created (source, created_at)',
  'SELECT ''idx_source_created already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

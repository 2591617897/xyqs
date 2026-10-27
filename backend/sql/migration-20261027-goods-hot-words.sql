-- ============================================================
-- 搜索热词（闲置列表「大家都在搜」）
--
-- 数据来源：C 端每次「有结果的关键词搜索」写一条日志，接口按近 N 天聚合。
-- 不用 Redis / ES：校园体量下这张表一天的增量是「日活 × 人均不同搜索词数」，
-- 加索引后聚合查询在毫秒级，没必要为热词引入额外中间件。
--
-- 核心设计：唯一键 uk_day_user_word (search_day, user_id, norm_keyword)
--   1) 同一个人同一天搜同一个词只算 1 次 → 刷榜天然失效（一个人刷一万次也只是一票）；
--   2) 日志量可控，不会因为「用户反复点同一个历史词」而膨胀；
--   3) 游客统一记 user_id = 0，因此游客的同词同日也只算 1 次（会略微低估游客热度，
--      这对热词榜是可接受的偏差——校园用户绝大多数已登录）。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_goods_search_log (
  id           BIGINT      NOT NULL PRIMARY KEY,
  keyword      VARCHAR(64) NOT NULL COMMENT '用户输入原文（已截断）',
  norm_keyword VARCHAR(64) NOT NULL COMMENT '归一化关键词：按空格拆词后重新拼接，聚合口径',
  user_id      BIGINT      NOT NULL DEFAULT 0 COMMENT '搜索者；游客记 0（唯一键需要非 NULL）',
  school_id    BIGINT      DEFAULT NULL COMMENT '搜索时的学校范围，NULL=未限定',
  city         VARCHAR(32) DEFAULT NULL COMMENT '同城搜索的城市名',
  result_count INT         NOT NULL DEFAULT 0 COMMENT '该次搜索命中条数；0 的不进热词榜',
  search_day   DATE        NOT NULL COMMENT '按天分桶，唯一键的一部分',
  created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_day_user_word (search_day, user_id, norm_keyword),
  KEY idx_day_result (search_day, result_count),
  KEY idx_norm_day (norm_keyword, search_day)
) COMMENT '闲置搜索词日志（热词榜数据源，保留 90 天）';

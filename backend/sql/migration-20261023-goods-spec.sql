-- 2026-10-23 闲置发布与详情对齐：新增规格与成色字段
-- 适用已有数据库（全新部署由 schema.sql 覆盖，无需执行本脚本）
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p guizhou_campus_fun < migration-20261023-goods-spec.sql

USE guizhou_campus_fun;

ALTER TABLE t_goods
  ADD COLUMN condition_level  TINYINT      DEFAULT NULL COMMENT '成色: 1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹' AFTER category_id,
  ADD COLUMN purchase_time    VARCHAR(20)  DEFAULT NULL COMMENT '入手时间, 如: 2023年9月' AFTER condition_level,
  ADD COLUMN usage_frequency  VARCHAR(20)  DEFAULT NULL COMMENT '使用频率: 每天使用/经常使用/偶尔使用/几乎没用过' AFTER purchase_time,
  ADD COLUMN source           VARCHAR(20)  DEFAULT NULL COMMENT '来源渠道: 自购自用/朋友赠送/活动奖品/实验室配发/其他' AFTER usage_frequency,
  ADD COLUMN warranty         VARCHAR(20)  DEFAULT NULL COMMENT '保修状态: 官方保修中/官方保修已过/不确定' AFTER source,
  ADD COLUMN support_trial    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否支持面交试机 0否 1是' AFTER warranty;

-- 历史数据回填：老商品全部标记为支持面交试机（校园自提场景默认值）
UPDATE t_goods SET support_trial = 1 WHERE support_trial IS NULL;

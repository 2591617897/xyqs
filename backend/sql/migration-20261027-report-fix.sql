-- ---------------------------------------------------------------------------
-- 举报 / 意见反馈修复（2026-10-27）
--
-- 背景：utils/request.js 导出的是对象 { request, get, post, put, del }，
--       举报页误写成 http({ url, method, data })，运行期抛
--       "TypeError: http is not a function"，请求根本发不出去。
--       修好后才暴露出下面这条数据库约束问题（以前连请求都没发出去）。
--
-- 本脚本幂等，可重复执行。
-- ---------------------------------------------------------------------------

USE guizhou_campus_fun;

-- 1) FEEDBACK（意见反馈）没有举报对象，target_id 为 NULL，
--    但建表时是 NOT NULL 且无默认值 → 提交反馈直接 500
--    "Field 'target_id' doesn't have a default value"。
--    放开空约束即可；uk_target_reporter 里含 NULL 的行不参与去重
--    （MySQL 唯一索引允许多行 NULL），所以每条反馈各自成单，符合预期。
SET @sql := (
  SELECT IF(
    (SELECT IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_report_ticket'
        AND COLUMN_NAME = 'target_id') = 'NO',
    'ALTER TABLE t_report_ticket MODIFY COLUMN target_id BIGINT NULL COMMENT ''被举报对象ID；FEEDBACK（意见反馈）为NULL''',
    'SELECT ''skip: target_id 已可为空'''
  )
);
PREPARE st FROM @sql;
EXECUTE st;
DEALLOCATE PREPARE st;

-- 2) 意见反馈在后台列表里要有标识，避免和「内容举报」混在一起。
--    （仅补注释，不改数据；后台按 target_type = ''FEEDBACK'' 过滤即可）
ALTER TABLE t_report_ticket
  MODIFY COLUMN target_type VARCHAR(20) NOT NULL
  COMMENT 'POST WALL GOODS COMMENT USER MESSAGE FEEDBACK';

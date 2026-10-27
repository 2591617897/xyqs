-- ============================================================================
-- 微信学生身份认证（2026-10-27）
--
-- 背景：
--   原来的认证只有一条路——用户上传学生证照片 + 学号，运营人工审核（或 auto-approve
--   直接放行）。现在接入微信官方「学生身份快速验证」：用户在小程序里授权，微信侧核验
--   学信网学籍，后端拿 code 换结果，通过即可免材料直接认证。
--
-- 设计要点：
--   1) 两条通道共用 verify_status（0未认证 1审核中 2已认证 3驳回），
--      新增 verify_source 区分「靠什么过的」，后台看报表/申诉时能分辨。
--   2) 微信接口只返回 bind_status / is_student，**不返回学校**，
--      所以 school_id 仍由用户自己选，认证时必须是有效学校。
--   3) 微信接口不返回姓名/学号，所以走微信通道的用户 real_name / student_no 保持为空，
--      后续页面不要假设「已认证 == 有学号」。
--   4) code 两小时有效、一次性，不落库（避免多实例重放与隐私留存）；
--      只落最终结论与核验时间。
--
-- ⚠️ 本脚本是**幂等**的：列/索引已存在会跳过，可反复执行。
--    必须先跑本脚本再启动 campus-auth / campus-user，
--    否则 User 实体带上这些字段后，任何 select t_user 都会
--    Unknown column 'verify_source' —— 表现为「微信登录失败，请重试」。
--
-- 执行：mysql -uroot -p -D <库名> < migration-20261027-wx-student-verify.sql
--       库名由命令行 -D 指定（本地 guizhou_campus_fun / 线上按实际库名），
--       脚本内不写死 USE，避免换库名后整段失败。
-- ============================================================================
SET @db := DATABASE();

-- 1) verify_source：认证来源 0未认证 1学生证材料 2微信学生认证
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'verify_source') = 0,
    'ALTER TABLE t_user ADD COLUMN verify_source TINYINT NOT NULL DEFAULT 0 COMMENT ''认证来源 0未认证 1学生证材料 2微信学生认证'' AFTER verify_status',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) wx_student_verified：微信学生身份是否已核验通过
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'wx_student_verified') = 0,
    'ALTER TABLE t_user ADD COLUMN wx_student_verified TINYINT NOT NULL DEFAULT 0 COMMENT ''微信学生身份是否已核验通过 0否 1是'' AFTER verify_source',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) wx_student_status：微信返回的 bind_status
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'wx_student_status') = 0,
    'ALTER TABLE t_user ADD COLUMN wx_student_status TINYINT DEFAULT NULL COMMENT ''微信返回的 bind_status 1未绑定 2审核中 3已绑定'' AFTER wx_student_verified',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) wx_student_verify_at：微信学生认证通过时间
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'wx_student_verify_at') = 0,
    'ALTER TABLE t_user ADD COLUMN wx_student_verify_at DATETIME DEFAULT NULL COMMENT ''微信学生认证通过时间（毕业后微信侧自动解绑，本列只记录我方通过时间）'' AFTER wx_student_status',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) 历史数据回填：已认证的用户一定走的是学生证材料通道
UPDATE t_user SET verify_source = 1 WHERE verify_status = 2 AND verify_source = 0;

-- 6) 后台筛选「微信认证用户」用（量级小，单列索引足够）
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_user' AND INDEX_NAME = 'idx_verify_source') = 0,
    'ALTER TABLE t_user ADD KEY idx_verify_source (verify_source)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7) 自检：应输出 4 列 + 1 索引
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_user'
   AND COLUMN_NAME IN ('verify_source','wx_student_verified','wx_student_status','wx_student_verify_at')
 ORDER BY ORDINAL_POSITION;

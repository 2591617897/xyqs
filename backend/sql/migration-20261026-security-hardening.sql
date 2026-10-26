-- ============================================================================
-- 安全加固迁移（2026-10-26）
--
-- 对应代码改动：
--   1) 后台密码 MD5 → BCrypt（登录时自动升级，无需刷库）
--   2) t_user 手机号 / 学号 / 真实姓名、t_goods_order.contact 改为 AES-256-GCM 密文
--   3) 加密列无法等值查询，新增盲索引列 phone_hash / student_no_hash
--
-- 执行顺序：先执行本脚本，再启动服务（启动时会做列长度校验）。
-- 历史明文数据由 CAMPUS_MIGRATE_PLAINTEXT=true 启动一次自动加密，迁移完关闭该开关。
-- ============================================================================
USE guizhou_campus_fun;

-- ---------------------------------------------------------------
-- 1. 后台管理员：密码列扩容 + 口径说明
-- ---------------------------------------------------------------
-- BCrypt 固定 60 字符，留出余量给以后换 Argon2（约 100 字符）
ALTER TABLE t_admin_user
  MODIFY COLUMN password VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希（$2a$ 前缀）；历史 MD5 记录在下次登录成功时自动升级';

-- salt 列保留但不再使用：BCrypt 自带随机盐。历史 MD5 记录仍需要它，所以不能删。
ALTER TABLE t_admin_user
  MODIFY COLUMN salt VARCHAR(32) NOT NULL DEFAULT '' COMMENT '仅历史 MD5 记录使用，BCrypt 记录为空串';

-- 所有内置账号强制首次登录改密（含 admin / auditor）
UPDATE t_admin_user SET must_change_pwd = 1 WHERE password NOT LIKE '$2a$%' AND password NOT LIKE '$2b$%';

-- 可选：把初始口令直接换成 BCrypt（推荐）。生成方式见 docs/安全审计报告.md 的「密码迁移」一节，
-- 例如在任一服务的测试里调用 AdminPasswordUtil.hash("你的强口令") 得到 $2a$12$...，然后：
-- UPDATE t_admin_user SET password = '$2a$12$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', salt = ''
--  WHERE username = 'admin';

-- ---------------------------------------------------------------
-- 2. 用户表：加密列扩容 + 盲索引
-- ---------------------------------------------------------------
-- 密文格式 enc:v1:<base64(iv12|ct|tag16)>：原文 11 位手机号约 60 字符，32 位学号约 88 字符，
-- 统一放到 255，避免以后字段变长时静默截断（截断的密文解不开）。
ALTER TABLE t_user
  MODIFY COLUMN phone VARCHAR(255) DEFAULT NULL COMMENT '手机号（AES-256-GCM 密文，enc:v1: 前缀）',
  MODIFY COLUMN student_no VARCHAR(255) DEFAULT NULL COMMENT '学号（AES-256-GCM 密文）',
  MODIFY COLUMN real_name VARCHAR(255) DEFAULT NULL COMMENT '真实姓名（AES-256-GCM 密文）';

-- 盲索引：HMAC-SHA256，密钥在应用侧，库里无法反推明文
ALTER TABLE t_user
  ADD COLUMN phone_hash VARCHAR(64) DEFAULT NULL COMMENT '手机号盲索引（HMAC-SHA256，后台精确搜索用）' AFTER phone,
  ADD COLUMN student_no_hash VARCHAR(64) DEFAULT NULL COMMENT '学号盲索引（HMAC-SHA256，同校查重用）' AFTER student_no;

ALTER TABLE t_user
  ADD KEY idx_phone_hash (phone_hash),
  ADD KEY idx_student_no_hash (school_id, student_no_hash);

-- ---------------------------------------------------------------
-- 3. 订单联系方式加密
-- ---------------------------------------------------------------
ALTER TABLE t_goods_order
  MODIFY COLUMN contact VARCHAR(255) DEFAULT NULL COMMENT '联系方式（AES-256-GCM 密文）';

-- ---------------------------------------------------------------
-- 4. 数据库账号最小权限
-- ---------------------------------------------------------------
-- 应用长期用 root 连接是本次审计里风险最高的一项：一旦存在 SQL 注入（或某个接口拼了
-- order by 参数），攻击者就能 DROP 表、读 mysql.user、写文件。
-- 生产必须换成只能增删改查业务表的独立账号。
--
-- ★ 把下面的口令换成随机强口令，并通过环境变量 MYSQL_USER / MYSQL_PWD 注入应用
-- ★ 生产不要给 campus_app 任何 DDL / GRANT / FILE 权限
--
-- CREATE USER IF NOT EXISTS 'campus_app'@'%' IDENTIFIED BY 'CHANGE_ME_强口令';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON guizhou_campus_fun.* TO 'campus_app'@'%';
-- -- 只读报表账号（可选，接 BI / 数据看板用）
-- CREATE USER IF NOT EXISTS 'campus_ro'@'%' IDENTIFIED BY 'CHANGE_ME_另一个强口令';
-- GRANT SELECT ON guizhou_campus_fun.* TO 'campus_ro'@'%';
-- FLUSH PRIVILEGES;
--
-- 实施后可自查：
-- SHOW GRANTS FOR 'campus_app'@'%';

-- ---------------------------------------------------------------
-- 5. 校验
-- ---------------------------------------------------------------
-- 执行完应看到 3 个新列与 2 个新索引
-- SHOW COLUMNS FROM t_user LIKE '%hash%';
-- 迁移后不应再有明文（应全部以 enc:v1: 开头）
-- SELECT COUNT(*) FROM t_user WHERE phone IS NOT NULL AND phone <> '' AND phone NOT LIKE 'enc:v1:%';

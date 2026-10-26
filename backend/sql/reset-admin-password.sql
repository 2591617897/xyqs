-- ============================================================================
-- 后台管理员密码重置（应急用）
--
-- 适用场景：管理员账号被误删 / 密码字段被清空 / 忘记密码，导致后台完全登不进去。
--
-- 用法：
--   1) 生成 BCrypt 哈希（cost=12，与 AdminPasswordUtil 一致）：
--        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(12);
--        String hash = enc.encode("你的新口令");
--      或在任意服务的测试里 AdminPasswordUtil.hash("你的新口令")
--   2) 把下面的哈希换成你自己的，执行本脚本
--   3) 无需重启 campus-admin（密码与 token_version 每次请求都从库里读）
--
-- 说明：
--   - salt 必须置空串：BCrypt 自带随机盐，留着旧盐只会被历史 MD5 分支误用
--   - token_version + 1：让所有已签发的旧令牌当场失效
--   - fail_count / locked_until 清零：解除「连续失败锁定」，否则重置完还是登不进去
--   - must_change_pwd=1：登录后会强制改密（安全基线）。本地演示想跳过，
--     要么改成 0，要么给 campus-admin 加环境变量 CAMPUS_ADMIN_FORCE_PWD_CHANGE=false
-- ============================================================================
USE guizhou_campus_fun;

-- 当前口令：admin = Campus@2026#Temp / auditor = Campus@2026#Audit
UPDATE t_admin_user
   SET password        = '$2a$12$Tl3Kzxk6USKwbr3/iH1b5uL2aYI9jH12yXTRUW2NKIfM3ZJ/Lb2rS',
       salt            = '',
       must_change_pwd = 1,
       fail_count      = 0,
       locked_until    = NULL,
       status          = 1,
       token_version   = token_version + 1
 WHERE username = 'admin';

UPDATE t_admin_user
   SET password        = '$2a$12$1k/SUJDWM1FBGa0xuYCos.92YeVSx2nEMAoW9WmzQGI9uIeMruw0W',
       salt            = '',
       must_change_pwd = 1,
       fail_count      = 0,
       locked_until    = NULL,
       status          = 1,
       token_version   = token_version + 1
 WHERE username = 'auditor';

-- 如果账号整行被删掉了，用下面这段补回来（id 与初始化脚本一致，角色授权会跟着生效）
-- INSERT INTO t_admin_user
--   (id, username, password, salt, real_name, super_flag, data_scope, status, must_change_pwd, fail_count, token_version)
-- VALUES
--   (900000000000000001, 'admin',   '$2a$12$Tl3Kzxk6USKwbr3/iH1b5uL2aYI9jH12yXTRUW2NKIfM3ZJ/Lb2rS', '', '超级管理员', 1, 1, 1, 1, 0, 1),
--   (900000000000000002, 'auditor', '$2a$12$1k/SUJDWM1FBGa0xuYCos.92YeVSx2nEMAoW9WmzQGI9uIeMruw0W', '', '内容审核员', 0, 1, 1, 1, 0, 1)
-- ON DUPLICATE KEY UPDATE
--   password      = VALUES(password),
--   salt          = VALUES(salt),
--   status        = 1,
--   fail_count    = 0,
--   locked_until  = NULL,
--   token_version = token_version + 1;

-- ---------------------------------------------------------------------------
-- 校验：password 应以 $2a$12$ 开头且长度 60，fail_count=0，locked_until 为空
-- ---------------------------------------------------------------------------
SELECT id, username, super_flag, status, must_change_pwd, fail_count, locked_until,
       token_version, LEFT(password, 7) AS algo, LENGTH(password) AS pwd_len
  FROM t_admin_user;

-- ============================================================
-- 账号注销（2026-10-27）
--
-- 语义：注销 ≠ 立即删除。
--   1. 注销后账号进入「注销中」状态，不能登录使用；重新登录会询问是否恢复；
--   2. 昵称落库为「已注销用户」、头像/简介/手机号/校园认证被清空，
--      已发布内容照常展示，作者显示为「已注销用户」；
--   3. 保留期内（默认 365 天）重新登录可恢复，昵称/头像/简介从 *_bak 还原；
--   4. 满保留期由定时任务清除全部个人信息（含 openid 占位），内容行保留。
--
-- 为什么用新列而不是复用 status：
--   status=0 是「后台封禁」，解封流程与注销恢复完全不同。混用会让运营
--   点一下「解封」就把注销账号放回来，注销统计也会失真。
-- ============================================================

ALTER TABLE t_user
  ADD COLUMN cancelled_at datetime NULL
    COMMENT '注销申请时间，NULL=正常账号；保留期内重新登录可恢复' AFTER mute_until,
  ADD COLUMN cancel_nickname_bak varchar(64) NULL
    COMMENT '注销前昵称，恢复时还原；满保留期清理时删除' AFTER cancelled_at,
  ADD COLUMN cancel_avatar_bak varchar(255) NULL
    COMMENT '注销前头像，恢复时还原' AFTER cancel_nickname_bak,
  ADD COLUMN cancel_bio_bak varchar(255) NULL
    COMMENT '注销前个人简介，恢复时还原' AFTER cancel_avatar_bak,
  ADD COLUMN purged_at datetime NULL
    COMMENT '个人信息清理完成时间（满保留期后），NULL=尚未清理' AFTER cancel_bio_bak,
  ADD KEY idx_cancelled_at (cancelled_at, purged_at);

-- 历史数据无需回填：cancelled_at 默认 NULL 即「正常账号」。

-- 回滚：
-- ALTER TABLE t_user
--   DROP KEY idx_cancelled_at,
--   DROP COLUMN cancelled_at, DROP COLUMN cancel_nickname_bak,
--   DROP COLUMN cancel_avatar_bak, DROP COLUMN cancel_bio_bak,
--   DROP COLUMN purged_at;

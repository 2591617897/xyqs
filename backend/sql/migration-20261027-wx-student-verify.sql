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
-- 执行顺序：先跑本脚本，再启动 campus-auth / campus-user。
-- ============================================================================
USE guizhou_campus_fun;

ALTER TABLE t_user
  ADD COLUMN verify_source TINYINT NOT NULL DEFAULT 0
    COMMENT '认证来源 0未认证 1学生证材料 2微信学生认证' AFTER verify_status,
  ADD COLUMN wx_student_verified TINYINT NOT NULL DEFAULT 0
    COMMENT '微信学生身份是否已核验通过 0否 1是' AFTER verify_source,
  ADD COLUMN wx_student_status TINYINT DEFAULT NULL
    COMMENT '微信返回的 bind_status 1未绑定 2审核中 3已绑定' AFTER wx_student_verified,
  ADD COLUMN wx_student_verify_at DATETIME DEFAULT NULL
    COMMENT '微信学生认证通过时间（毕业到期后微信侧自动解绑，本列仅记录我方通过时间）' AFTER wx_student_status;

-- 历史数据回填：已认证的用户一定走的是学生证材料通道
UPDATE t_user SET verify_source = 1 WHERE verify_status = 2 AND verify_source = 0;

-- 后台筛选「微信认证用户」用（量级小，单列索引足够）
ALTER TABLE t_user ADD KEY idx_verify_source (verify_source);

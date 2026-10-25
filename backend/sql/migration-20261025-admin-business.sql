-- ============================================================
-- 迁移：校园趣事后台管理业务库（campus-admin 第二期 / 内容·用户·互动·广告·看板）
-- 背景：第一期只解决了「谁能进后台、进了能干什么被记下来」，
--       业务动作还没有落点：帖子违规要由运营去数据库改 status、
--       用户投诉没有工单可追踪、广告位只能改代码、看板数字要人肉 SQL。
--       本脚本建立后台自己的业务表，全部只存「后台产生的治理数据」，
--       内容本体（t_post / t_wall_post / t_goods）的状态仍复用各自既有的 status 列，
--       不新增并列状态，避免前后端出现两个真相。
-- 设计约定：
--   1) 状态复用：帖子/墙/商品的可见性只认自己的 status，
--      本库的 t_content_audit 只做「谁在什么时候做了什么」的流水，不承载最终态。
--   2) 投影列：t_user.mute_until（第一期已加）是禁言的高频读投影，
--      真正事实源是这里的 t_user_mute；两者由 service 在同一事务里写。
--   3) 幂等：所有新表 CREATE TABLE IF NOT EXISTS；
--      新增索引用 information_schema 判定后 PREPARE/EXECUTE。
-- 执行：mysql -u root -p guizhou_campus_fun < migration-20261025-admin-business.sql
-- ============================================================
USE guizhou_campus_fun;

-- ------------------------------------------------------------
-- 1) 内容审核流水。
--    一次处置一行：隐藏/恢复/删除/驳回都留痕，供申诉与审计追溯。
--    匿名内容（表白墙）在这里留存 author_id，前台仍匿名展示，
--    「前台匿名 + 后台可追溯」两件事由这条流水承担。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_content_audit (
  id             BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  target_type    VARCHAR(20)  NOT NULL COMMENT 'POST帖子 WALL表白墙 GOODS商品 P_COMMENT帖子评论 W_COMMENT墙评论 G_COMMENT商品评论',
  target_id      BIGINT       NOT NULL COMMENT '业务对象ID',
  title          VARCHAR(120) DEFAULT NULL COMMENT '内容标题/摘要快照，原内容被删后仍可追溯',
  content_brief  VARCHAR(500) DEFAULT NULL COMMENT '正文前 200 字快照',
  images         VARCHAR(1000) DEFAULT NULL COMMENT '图片快照，逗号分隔',
  author_id      BIGINT       DEFAULT NULL COMMENT '作者ID（匿名内容亦留存，仅后台可见）',
  author_name    VARCHAR(64)  DEFAULT NULL COMMENT '作者昵称快照',
  school_id      BIGINT       DEFAULT NULL COMMENT '所属校区，用于数据权限过滤',
  action         VARCHAR(20)  NOT NULL COMMENT 'HIDE隐藏 SHOW恢复 DELETE删除 REJECT驳回 PASS通过 WARN警告',
  reason_type    VARCHAR(32)  DEFAULT NULL COMMENT '违规类型：色情低俗/暴力血腥/诈骗/广告骚扰/人身攻击/虚假信息/侵权/其他',
  reason         VARCHAR(500) DEFAULT NULL COMMENT '处置说明，驳回时必填',
  admin_id       BIGINT       DEFAULT NULL COMMENT '操作管理员ID',
  admin_name     VARCHAR(64)  DEFAULT NULL COMMENT '操作管理员账号快照',
  source         TINYINT      NOT NULL DEFAULT 1 COMMENT '1后台人工 2系统自动（敏感词） 3举报联动',
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_target (target_type, target_id, id),
  KEY idx_author (author_id, created_at),
  KEY idx_school (school_id, created_at),
  KEY idx_admin (admin_id, created_at),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '内容处置流水（后台治理留痕）';

-- ------------------------------------------------------------
-- 2) 举报工单。
--    ticket_no 对外可展示；uk_target_reporter 保证同一人对同一内容只举报一次，
--    重复举报走 UPDATE 累加 report_count，不让工单表被刷。
--    处理完成后通过 handle_result 联动内容处置。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_report_ticket (
  id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  ticket_no     VARCHAR(32)  NOT NULL COMMENT '工单号，如 RP20261025000001',
  target_type   VARCHAR(20)  NOT NULL COMMENT 'POST WALL GOODS COMMENT USER MESSAGE',
  target_id     BIGINT       NOT NULL COMMENT '被举报对象ID',
  target_brief  VARCHAR(500) DEFAULT NULL COMMENT '被举报内容快照',
  reporter_id   BIGINT       DEFAULT NULL COMMENT '举报人ID，匿名举报为NULL',
  reporter_name VARCHAR(64)  DEFAULT NULL COMMENT '举报人昵称快照',
  reason_type   VARCHAR(32)  NOT NULL COMMENT '举报类型：色情低俗/暴力血腥/诈骗/广告骚扰/人身攻击/虚假信息/侵权/其他',
  reason_desc   VARCHAR(500) DEFAULT NULL COMMENT '举报描述',
  images        VARCHAR(1000) DEFAULT NULL COMMENT '举报凭证图片，逗号分隔',
  report_count  INT          NOT NULL DEFAULT 1 COMMENT '同一对象被举报累计次数',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2已驳回',
  handle_result VARCHAR(32)  DEFAULT NULL COMMENT '处理结果：下架内容/禁言作者/封禁作者/无违规/其他',
  handle_remark VARCHAR(500) DEFAULT NULL COMMENT '处理说明',
  handler_id    BIGINT       DEFAULT NULL COMMENT '处理人管理员ID',
  handler_name  VARCHAR(64)  DEFAULT NULL COMMENT '处理人账号快照',
  school_id     BIGINT       DEFAULT NULL COMMENT '所属校区，用于数据权限过滤',
  handled_at    DATETIME     DEFAULT NULL COMMENT '处理时间',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_no (ticket_no),
  UNIQUE KEY uk_target_reporter (target_type, target_id, reporter_id),
  KEY idx_status (status, created_at),
  KEY idx_target (target_type, target_id),
  KEY idx_school (school_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '举报工单';

-- ------------------------------------------------------------
-- 3) 敏感词。
--    level 决定命中后怎么办：1仅记录 2替换成 replace_to 3禁止发布。
--    全量载入内存做前缀树/HashMap 匹配，改词后由后台「刷新缓存」下发。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sensitive_word (
  id         BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  word       VARCHAR(64)  NOT NULL COMMENT '敏感词',
  level      TINYINT      NOT NULL DEFAULT 2 COMMENT '1提示（仅记录） 2替换 3禁止发布',
  replace_to VARCHAR(64)  DEFAULT NULL COMMENT '替换目标，level=2 时生效，默认 ***',
  category   VARCHAR(32)  DEFAULT NULL COMMENT '分类：色情/暴力/政治/广告/诈骗/其他',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  hit_count  INT          NOT NULL DEFAULT 0 COMMENT '命中次数，用于运营复盘',
  remark     VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_word (word),
  KEY idx_status (status),
  KEY idx_level (level, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '敏感词库';

-- ------------------------------------------------------------
-- 4) 禁言记录。
--    事实源。投影列 t_user.mute_until 在同一事务里同步，C 端判断只读那一列。
--    【status 用 NULL 表示已解除，不是 0】——这是刻意的：
--    MySQL 唯一索引允许多个 NULL，uk_user_scope_active(user_id, scope, status)
--    才能表达「同一用户同一范围同时最多一条生效记录」，
--    而历史的多条已解除记录互不冲突（解除两次也不会撞唯一键）。
--    若把解除写成 0，第二次解除就会 DuplicateKey。
--    到期由定时任务扫描 idx_end_at 自动置 NULL。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user_mute (
  id           BIGINT      NOT NULL PRIMARY KEY COMMENT '雪花ID',
  user_id      BIGINT      NOT NULL COMMENT '被禁言用户',
  scope        TINYINT     NOT NULL DEFAULT 1 COMMENT '1全站 2发帖 3评论 4私信',
  minutes      INT         NOT NULL DEFAULT 0 COMMENT '禁言时长（分钟），0 表示永久',
  reason_type  VARCHAR(32) DEFAULT NULL COMMENT '原因类型',
  reason       VARCHAR(500) DEFAULT NULL COMMENT '禁言原因',
  start_at     DATETIME    NOT NULL COMMENT '生效时间',
  end_at       DATETIME    DEFAULT NULL COMMENT '到期时间，永久禁言为NULL',
  status       TINYINT     DEFAULT 1 COMMENT '1生效中 NULL已解除（见上方说明）',
  released_at  DATETIME    DEFAULT NULL COMMENT '实际解除时间',
  release_type TINYINT     DEFAULT NULL COMMENT '解除方式 1人工 2到期自动',
  operator_id  BIGINT      DEFAULT NULL COMMENT '操作管理员ID',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作管理员账号快照',
  created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_scope_active (user_id, scope, status),
  KEY idx_user (user_id, created_at),
  KEY idx_end (end_at),
  KEY idx_status (status, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '禁言记录（事实源）';

-- ------------------------------------------------------------
-- 5) 黑名单。
--    与 t_user.status=0 联动：拉黑时把用户置为封禁，解除时恢复。
--    status 同样用 NULL 表示已解除（理由同 t_user_mute），
--    保证「同一用户同时只有一条生效记录」且可反复拉黑/解除。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user_blacklist (
  id            BIGINT      NOT NULL PRIMARY KEY COMMENT '雪花ID',
  user_id       BIGINT      NOT NULL COMMENT '被拉黑用户',
  user_name     VARCHAR(64) DEFAULT NULL COMMENT '昵称快照',
  reason_type   VARCHAR(32) DEFAULT NULL COMMENT '原因类型',
  reason        VARCHAR(500) DEFAULT NULL COMMENT '拉黑原因',
  status        TINYINT     DEFAULT 1 COMMENT '1生效中 NULL已解除',
  released_at   DATETIME    DEFAULT NULL COMMENT '解除时间',
  operator_id   BIGINT      DEFAULT NULL COMMENT '操作管理员ID',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作管理员账号快照',
  created_at    DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_active (user_id, status),
  KEY idx_user (user_id),
  KEY idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '用户黑名单';

-- ------------------------------------------------------------
-- 6) 广告位。
--    按校区/地区维度维护：scope_type 决定命中范围，
--    school_id 与 city 二选一（参考 t_admin_region 的匹配口径）。
--    audit_status 与 status 分离：过了审才能上架，到期自动下架由定时任务处理。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ad_slot (
  id           BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  name         VARCHAR(64)  NOT NULL COMMENT '广告位名称',
  code         VARCHAR(64)  NOT NULL COMMENT '广告位编码，小程序按 code 拉取',
  position     TINYINT      NOT NULL DEFAULT 1 COMMENT '1首页Banner 2频道页 3表白墙 4商品列表 5开屏 6个人中心',
  scope_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '投放范围 1全国 2城市 3校区',
  city         VARCHAR(32)  DEFAULT NULL COMMENT 'scope_type=2 时的城市',
  school_id    BIGINT       DEFAULT NULL COMMENT 'scope_type=3 时的校区ID',
  size_desc    VARCHAR(64)  DEFAULT NULL COMMENT '尺寸说明，如 750x300',
  max_count    INT          NOT NULL DEFAULT 3 COMMENT '同时在线素材上限',
  sort         INT          NOT NULL DEFAULT 0 COMMENT '排序，大到小',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0下架 1上架',
  audit_status TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  audit_remark VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
  start_at     DATETIME     DEFAULT NULL COMMENT '投放开始时间',
  end_at       DATETIME     DEFAULT NULL COMMENT '投放结束时间',
  remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code),
  KEY idx_scope (scope_type, city, school_id),
  KEY idx_status (status, audit_status, sort),
  KEY idx_position (position, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '广告位';

-- ------------------------------------------------------------
-- 7) 广告素材。
--    挂在广告位下，一个广告位多张素材轮播。
--    link_type 决定点击跳转：站内帖子/商品走小程序路由，外链走 webview。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ad_material (
  id           BIGINT        NOT NULL PRIMARY KEY COMMENT '雪花ID',
  slot_id      BIGINT        NOT NULL COMMENT '所属广告位ID',
  slot_name    VARCHAR(64)   DEFAULT NULL COMMENT '广告位名称快照',
  title        VARCHAR(120)  NOT NULL COMMENT '素材标题',
  image        VARCHAR(255)  NOT NULL COMMENT '素材图片地址',
  link_type    TINYINT       NOT NULL DEFAULT 0 COMMENT '0无跳转 1帖子 2商品 3外链 4小程序页面',
  link_value   VARCHAR(255)  DEFAULT NULL COMMENT '跳转目标：帖子ID/商品ID/URL/页面路径',
  sort         INT           NOT NULL DEFAULT 0 COMMENT '排序，大到小',
  status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0下架 1上架',
  audit_status TINYINT       NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  audit_remark VARCHAR(255)  DEFAULT NULL COMMENT '审核意见',
  start_at     DATETIME      DEFAULT NULL COMMENT '投放开始时间',
  end_at       DATETIME      DEFAULT NULL COMMENT '投放结束时间',
  view_count   INT           NOT NULL DEFAULT 0 COMMENT '曝光数',
  click_count  INT           NOT NULL DEFAULT 0 COMMENT '点击数',
  remark       VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_slot (slot_id, sort),
  KEY idx_status (status, audit_status),
  KEY idx_time (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '广告素材';

-- ------------------------------------------------------------
-- 8) 通知公告。
--    target_type 决定推给谁：1全体管理员（后台内）2小程序全体用户 3指定校区用户。
--    草稿->已发布->已下线，publish_at 可预约未来发布（定时任务到点切状态）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_notice (
  id           BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  title        VARCHAR(120) NOT NULL COMMENT '标题',
  content      VARCHAR(4000) NOT NULL COMMENT '正文',
  type         TINYINT      NOT NULL DEFAULT 1 COMMENT '1系统公告 2运营活动 3违规警示',
  target_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '1后台管理员 2小程序全体 3指定校区',
  school_id    BIGINT       DEFAULT NULL COMMENT 'target_type=3 时的校区ID',
  level        TINYINT      NOT NULL DEFAULT 1 COMMENT '1普通 2重要 3紧急',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已下线',
  publish_at   DATETIME     DEFAULT NULL COMMENT '发布时间（可预约）',
  offline_at   DATETIME     DEFAULT NULL COMMENT '下线时间',
  publisher_id BIGINT       DEFAULT NULL COMMENT '发布人管理员ID',
  publisher_name VARCHAR(64) DEFAULT NULL COMMENT '发布人账号快照',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status, publish_at),
  KEY idx_target (target_type, school_id, status),
  KEY idx_level (level, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '通知公告';

-- ------------------------------------------------------------
-- 9) 看板日表（物化）。
--    统计口径分散在 5 个服务的表里，实时聚合会把后台拖垮，
--    每日由定时任务（或后台「重算指标」按钮）落一行，看板直接读这张表。
--    school_id=0 表示全平台汇总行。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_dashboard_daily (
  id            BIGINT   NOT NULL PRIMARY KEY COMMENT '雪花ID',
  stat_date     DATE     NOT NULL COMMENT '统计日期',
  school_id     BIGINT   NOT NULL DEFAULT 0 COMMENT '校区ID，0 表示全平台',
  new_users     INT      NOT NULL DEFAULT 0 COMMENT '新增用户',
  active_users  INT      NOT NULL DEFAULT 0 COMMENT '活跃用户（当日有内容/互动）',
  new_posts     INT      NOT NULL DEFAULT 0 COMMENT '新增帖子',
  new_wall      INT      NOT NULL DEFAULT 0 COMMENT '新增表白墙',
  new_goods     INT      NOT NULL DEFAULT 0 COMMENT '新增商品',
  new_orders    INT      NOT NULL DEFAULT 0 COMMENT '新增意向单',
  new_messages  INT      NOT NULL DEFAULT 0 COMMENT '新增私信',
  audit_count   INT      NOT NULL DEFAULT 0 COMMENT '内容处置次数',
  report_count  INT      NOT NULL DEFAULT 0 COMMENT '新增举报',
  handled_report INT     NOT NULL DEFAULT 0 COMMENT '已处理举报',
  mute_count    INT      NOT NULL DEFAULT 0 COMMENT '禁言人次',
  ban_count     INT      NOT NULL DEFAULT 0 COMMENT '封禁人次',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_date_school (stat_date, school_id),
  KEY idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '看板日表（物化统计）';

-- ------------------------------------------------------------
-- 10) 实名审核流水。
--     t_user.verify_status 是最终态（0未认证 1审核中 2已认证 3驳回），
--     这里记录每一次审核动作与驳回原因，供用户申诉与后台追溯。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user_verify_log (
  id           BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  user_id      BIGINT       NOT NULL COMMENT '被审核用户',
  user_name    VARCHAR(64)  DEFAULT NULL COMMENT '昵称快照',
  school_id    BIGINT       DEFAULT NULL COMMENT '认证学校',
  school_name  VARCHAR(64)  DEFAULT NULL COMMENT '学校名称快照',
  real_name    VARCHAR(32)  DEFAULT NULL COMMENT '提交的真实姓名',
  student_no   VARCHAR(32)  DEFAULT NULL COMMENT '提交的学号',
  verify_img   VARCHAR(255) DEFAULT NULL COMMENT '证明材料快照',
  from_status  TINYINT      DEFAULT NULL COMMENT '审核前状态',
  to_status    TINYINT      NOT NULL COMMENT '审核后状态 2通过 3驳回',
  reason       VARCHAR(500) DEFAULT NULL COMMENT '驳回原因，驳回时必填',
  admin_id     BIGINT       DEFAULT NULL COMMENT '审核管理员ID',
  admin_name   VARCHAR(64)  DEFAULT NULL COMMENT '审核管理员账号快照',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id, created_at),
  KEY idx_school (school_id, created_at),
  KEY idx_admin (admin_id, created_at),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '实名认证审核流水';

-- ------------------------------------------------------------
-- 11) 敏感词默认种子（可重复执行：uk_word + ON DUPLICATE KEY 不覆盖运营改动）
--     只塞最小可用集，避免污染运营后续维护的词库。
-- ------------------------------------------------------------
INSERT INTO t_sensitive_word (id, word, level, replace_to, category, status) VALUES
(920000000000000001, '加微信',   2, '****',  '广告', 1),
(920000000000000002, '加微',     2, '****',  '广告', 1),
(920000000000000003, '私聊赚钱', 3, NULL,    '诈骗', 1),
(920000000000000004, '刷单',     3, NULL,    '诈骗', 1),
(920000000000000005, '兼职日结', 2, '******','诈骗', 1),
(920000000000000006, '代考',     3, NULL,    '其他', 1),
(920000000000000007, '出售答案', 3, NULL,    '其他', 1)
ON DUPLICATE KEY UPDATE word = VALUES(word);

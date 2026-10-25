-- ============================================================
-- 迁移：校园趣事后台管理基础库（campus-admin 第一期 / RBAC + 审计）
-- 背景：现有 t_user 只承载 C 端小程序用户，没有任何管理员概念、
--       没有角色/菜单/权限点、没有操作留痕。小程序有没有管不到的地方：
--       内容没人审、违规用户没人封、运营配置要改数据库。
--       随着内容增长，小程序侧已经兜不住：内容没人审、违规用户没人封、
--       运营配置每次都要改数据库。
--       本脚本建立「独立于小程序用户体系」的后台账号与授权模型：
--         t_admin_user / t_admin_role / t_admin_menu / t_admin_user_role / t_admin_role_menu
--         t_admin_operation_log（操作审计）/ t_admin_login_log（登录留痕）
--       并给 t_user 补一列 mute_until（禁言到期时间），为后续禁言做投影位。
--       管理员令牌由 campus-admin 用自己的密钥 campus.admin.jwt.secret 签发，
--       与 C 端 campus.jwt.secret 物理隔离，网关靠 claim typ=admin 区分。
-- 执行：mysql -u root -p guizhou_campus_fun < migration-20261025-campus-admin.sql
-- 幂等：可重复执行——
--       新表一律 CREATE TABLE IF NOT EXISTS；
--       t_user.mute_until 与索引用 information_schema 判定后 PREPARE/EXECUTE，
--       MySQL 无 ADD COLUMN / ADD INDEX IF NOT EXISTS。
-- ============================================================
USE guizhou_campus_fun;

-- ------------------------------------------------------------
-- 0) t_user 幂等补 mute_until（禁言到期时间）
--    真正的禁言事实源是后续建的表，这里只是给 C 端高频判断留一个投影：
--    未来 post / message 服务判断「是否被禁言」只读这一列，
--    不必跨表子查询一张 admin 的表。NULL 表示未禁言。
-- ------------------------------------------------------------
SET @has_mute := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'mute_until'
);
SET @ddl := IF(@has_mute = 0,
  'ALTER TABLE t_user ADD COLUMN mute_until DATETIME DEFAULT NULL COMMENT ''禁言到期时间，NULL 表示未禁言'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_mute := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND INDEX_NAME = 'idx_mute_until'
);
SET @ddl := IF(@has_idx_mute = 0,
  'ALTER TABLE t_user ADD KEY idx_mute_until (mute_until)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 1) 管理员。独立于 t_user：不与小程序用户混表，避免 C 端 SQL 误操作到后台账号。
--    password = md5(明文 + salt) 小写 32 位，salt 每人一行。
--    fail_count / locked_until 做登录暴力破解风控（5 次锁 15 分钟）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_user (
  id              BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  username        VARCHAR(64)  NOT NULL COMMENT '登录账号',
  password        VARCHAR(64)  NOT NULL COMMENT 'md5(明文+salt) 小写32位',
  salt            VARCHAR(16)  NOT NULL COMMENT '密码盐，8-16位',
  real_name       VARCHAR(32)  DEFAULT NULL COMMENT '姓名',
  phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  email           VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
  avatar          VARCHAR(255) DEFAULT NULL COMMENT '头像（/uploads/... 相对路径）',
  super_flag      TINYINT      NOT NULL DEFAULT 0 COMMENT '1超级管理员（免权限校验、全量数据权限） 0普通管理员',
  data_scope      TINYINT      NOT NULL DEFAULT 1 COMMENT '数据权限范围 1全部 2仅辖区（由 t_admin_region_leader 决定）',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  must_change_pwd TINYINT      NOT NULL DEFAULT 1 COMMENT '1强制修改初始密码 0已修改',
  fail_count      INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数，登录成功后清零',
  locked_until    DATETIME     DEFAULT NULL COMMENT '锁定到期时间（连续失败达上限后锁定）',
  last_login_at   DATETIME     DEFAULT NULL COMMENT '最近登录时间',
  last_login_ip   VARCHAR(64)  DEFAULT NULL COMMENT '最近登录IP',
  remark          VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username),
  KEY idx_status (status),
  KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '后台管理员（独立于小程序用户体系）';

-- ------------------------------------------------------------
-- 2) 角色。code 全局唯一，前端/后端按 code 判断内置角色（SUPER_ADMIN 不可删改 code）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_role (
  id         BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  name       VARCHAR(64)  NOT NULL COMMENT '角色名称',
  code       VARCHAR(64)  NOT NULL COMMENT '角色编码，如 SUPER_ADMIN / CONTENT_AUDITOR',
  remark     VARCHAR(255) DEFAULT NULL COMMENT '备注',
  sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，大到小',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '后台角色';

-- ------------------------------------------------------------
-- 3) 菜单与权限点。
--    type: 1目录 2菜单 3按钮（只有 type=3 的 permission 才是真正的权限点，
--          但 type=2 的菜单也带一个 list 权限点，用于「能不能进这个页面」）。
--    component 是前端 views 下的相对路径，由后端下发、前端 import.meta.glob 动态加载，
--    两者必须一一对应（见 migration-20261025-campus-admin-init.sql）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_menu (
  id         BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶层',
  name       VARCHAR(64)  NOT NULL COMMENT '菜单名称',
  type       TINYINT      NOT NULL DEFAULT 2 COMMENT '1目录 2菜单 3按钮',
  path       VARCHAR(200) DEFAULT NULL COMMENT '前端路由路径，如 /content/posts',
  component  VARCHAR(200) DEFAULT NULL COMMENT '前端组件路径，如 content/PostList（按钮类型为空）',
  icon       VARCHAR(64)  DEFAULT NULL COMMENT 'Element Plus 图标名，如 Odometer',
  permission VARCHAR(128) DEFAULT NULL COMMENT '权限点字符串，如 admin:content:post:hide（目录可为空）',
  sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，大到小',
  visible    TINYINT      NOT NULL DEFAULT 1 COMMENT '1显示 0隐藏',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark     VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_permission (permission),
  KEY idx_parent (parent_id, sort),
  KEY idx_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '后台菜单与权限点';

-- ------------------------------------------------------------
-- 4) 管理员-角色。uk 兜底：并发重复绑定由唯一键挡住，
--    上层代码采用「先 delete 再批量 insert」，不让 DuplicateKey 冒到 500。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_user_role (
  id            BIGINT   NOT NULL PRIMARY KEY COMMENT '雪花ID',
  admin_user_id BIGINT   NOT NULL COMMENT '管理员ID',
  role_id       BIGINT   NOT NULL COMMENT '角色ID',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_role (admin_user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '管理员-角色关联';

-- ------------------------------------------------------------
-- 5) 角色-菜单（含按钮权限点）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_role_menu (
  id         BIGINT   NOT NULL PRIMARY KEY COMMENT '雪花ID',
  role_id    BIGINT   NOT NULL COMMENT '角色ID',
  menu_id    BIGINT   NOT NULL COMMENT '菜单/按钮权限ID',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_menu (role_id, menu_id),
  KEY idx_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '角色-菜单权限关联';

-- ------------------------------------------------------------
-- 6) 操作审计日志。写多读少，由后台异步批量落库，
--    切面只做入队，绝不拖慢业务请求。params 已脱敏并截断。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_operation_log (
  id          BIGINT        NOT NULL PRIMARY KEY COMMENT '雪花ID',
  admin_id    BIGINT        DEFAULT NULL COMMENT '操作管理员ID（登录失败等场景为NULL）',
  username    VARCHAR(64)   DEFAULT NULL COMMENT '操作账号快照',
  module      VARCHAR(32)   DEFAULT NULL COMMENT '业务模块 AUTH/CONTENT/USER/AD/REPORT/SYSTEM/NOTICE/CHAT/SENSITIVE',
  operation   VARCHAR(128)  DEFAULT NULL COMMENT '操作描述，取 @AuditLog 的 value',
  method      VARCHAR(200)  DEFAULT NULL COMMENT '类#方法 全限定名',
  uri         VARCHAR(200)  DEFAULT NULL COMMENT '请求URI',
  http_method VARCHAR(10)   DEFAULT NULL COMMENT 'GET/POST/PUT/DELETE',
  ip          VARCHAR(64)   DEFAULT NULL COMMENT '来源IP（取 X-Forwarded-For 首段）',
  ua          VARCHAR(500)  DEFAULT NULL COMMENT 'User-Agent 截断',
  params      VARCHAR(2000) DEFAULT NULL COMMENT '请求参数JSON，截断2000字符，敏感字段已脱敏为***',
  result      TINYINT       NOT NULL DEFAULT 0 COMMENT '0成功 1失败',
  error_msg   VARCHAR(500)  DEFAULT NULL COMMENT '失败信息截断',
  cost_ms     INT           NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_admin (admin_id, created_at),
  KEY idx_created (created_at),
  KEY idx_module (module, created_at),
  KEY idx_result (result, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '后台操作审计日志';

-- ------------------------------------------------------------
-- 7) 登录日志。成功失败都记，用于排查账号被盗用/暴力破解。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_login_log (
  id          BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  username    VARCHAR(64)  DEFAULT NULL COMMENT '登录账号（账号不存在时也有记录）',
  admin_id    BIGINT       DEFAULT NULL COMMENT '登录成功时的管理员ID',
  ip          VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
  ua          VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent 截断',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '1成功 0失败',
  fail_reason VARCHAR(200) DEFAULT NULL COMMENT '失败原因：密码错误/账号禁用/账号不存在/账号已锁定',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  KEY idx_username (username, created_at),
  KEY idx_created (created_at),
  KEY idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '后台登录日志';

-- ------------------------------------------------------------
-- 8) 组织架构（地区 / 校区树）。
--    不复用 t_school：那张表是 campus-auth 独占的身份认证学校字典，
--    后台的运营辖区和「身份认证可选学校」是两回事，这里只做软关联：
--      type=2 校区节点 -> ref_school_id 指向 t_school.id（不建外键）
--      type=1 地区节点 -> 用 province/city 反查 t_school 得到辖区学校集合
--    数据权限 DataScopeResolver 就靠这张表把「管理员辖区」翻译成 school_id 集合。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_region (
  id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点ID，0为顶层',
  name          VARCHAR(64)  NOT NULL COMMENT '地区名 / 校区名',
  type          TINYINT      NOT NULL DEFAULT 1 COMMENT '1地区 2校区',
  level         TINYINT      NOT NULL DEFAULT 1 COMMENT '层级 1省 2市 3校区',
  province      VARCHAR(32)  DEFAULT NULL COMMENT '省份（地区节点用于匹配 t_school.province）',
  city          VARCHAR(32)  DEFAULT NULL COMMENT '城市（地区节点用于匹配 t_school.city）',
  ref_school_id BIGINT       DEFAULT NULL COMMENT '校区节点关联的 t_school.id，地区节点为NULL',
  sort          INT          NOT NULL DEFAULT 0 COMMENT '排序，大到小',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ref_school (ref_school_id),
  KEY idx_parent (parent_id, sort),
  KEY idx_city (city),
  KEY idx_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '后台组织架构（地区/校区树）';

-- ------------------------------------------------------------
-- 9) 负责人。一个地区/校区可以有多个负责人（uk_region_admin 去重），
--    指派后该管理员的 data_scope 切成「仅辖区」。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_admin_region_leader (
  id            BIGINT   NOT NULL PRIMARY KEY COMMENT '雪花ID',
  region_id     BIGINT   NOT NULL COMMENT '地区/校区节点ID',
  admin_user_id BIGINT   NOT NULL COMMENT '负责人（t_admin_user.id）',
  leader_type   TINYINT  NOT NULL DEFAULT 1 COMMENT '1负责人 2协管',
  status        TINYINT  NOT NULL DEFAULT 1 COMMENT '1生效 0已撤销',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_region_admin (region_id, admin_user_id),
  KEY idx_admin (admin_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '校区/地区负责人';

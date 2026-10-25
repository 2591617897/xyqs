-- ============================================================
-- 迁移：校园趣事后台管理初始化数据（管理员 / 角色 / 菜单权限树 / 角色授权）
-- 背景：配合 migration-20261025-campus-admin.sql 建好的 RBAC 表，
--       给出可直接登录的最小可用后台：
--         - 账号 admin（超级管理员，免权限校验、全量数据权限）
--         - 账号 auditor（内容审核员，只有内容+举报+聊天+用户查看权限）
--       以及 10 个顶层菜单 + 约 60 个权限点，前端菜单路由完全由这张表驱动。
-- 执行：mysql -u root -p guizhou_campus_fun < migration-20261025-campus-admin-init.sql
--       依赖：先执行 migration-20261025-campus-admin.sql
-- 幂等：可重复执行——
--       全部 INSERT ... ON DUPLICATE KEY UPDATE；
--       角色授权用 INSERT IGNORE + 从 t_admin_menu 反查 permission 前缀，
--       菜单权限点字符串改了之后重跑脚本能自动跟上。
--       ★ 注意：管理员的 ON DUPLICATE KEY UPDATE 刻意不包含 password/salt，
--         否则每次重跑都会把线上已改过的密码打回初始密码（等于留后门）。
-- ============================================================
USE guizhou_campus_fun;

-- ------------------------------------------------------------
-- 1) 管理员
--    password 用 MySQL 内置 MD5(CONCAT(明文, salt)) 计算，与后台
--    AdminPasswordUtil#md5Hex（小写 32 位）完全一致，salt 固定 'c4m2p9s1'。
--    初始密码 admin123，登录后 must_change_pwd=1 会被强制要求改密。
-- ------------------------------------------------------------
INSERT INTO t_admin_user
  (id, username, password, salt, real_name, super_flag, data_scope, status, must_change_pwd, remark)
VALUES
  (900000000000000001, 'admin',   MD5(CONCAT('admin123', 'c4m2p9s1')), 'c4m2p9s1', '超级管理员', 1, 1, 1, 1, '内置超管，不可删除'),
  (900000000000000002, 'auditor', MD5(CONCAT('admin123', 'c4m2p9s1')), 'c4m2p9s1', '内容审核员', 0, 1, 1, 1, '仅内容审核相关权限')
ON DUPLICATE KEY UPDATE
  real_name = VALUES(real_name),
  super_flag = VALUES(super_flag),
  data_scope = VALUES(data_scope),
  remark = VALUES(remark);

-- ------------------------------------------------------------
-- 2) 角色
-- ------------------------------------------------------------
INSERT INTO t_admin_role (id, name, code, remark, sort, status) VALUES
(910000000000000001, '超级管理员',   'SUPER_ADMIN',     '内置角色，免权限校验、全量数据权限，不可删除不可改code', 100, 1),
(910000000000000002, '内容审核员',   'CONTENT_AUDITOR', '帖子/表白墙/商品/评论/举报工单处理',                   90, 1),
(910000000000000003, '用户管理员',   'USER_MANAGER',    '用户封禁/禁言/拉黑/实名审核/互动管理',                 80, 1),
(910000000000000004, '运营',         'OPERATOR',        '广告位与素材投放、通知公告、敏感词、数据看板',         70, 1),
(910000000000000005, '校区负责人',   'REGION_LEADER',   '仅能看本辖区数据，只读为主',                           60, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), remark = VALUES(remark), sort = VALUES(sort), status = VALUES(status);

-- ------------------------------------------------------------
-- 3) 管理员-角色
-- ------------------------------------------------------------
INSERT INTO t_admin_user_role (id, admin_user_id, role_id) VALUES
(920000000000000001, 900000000000000001, 910000000000000001),
(920000000000000002, 900000000000000002, 910000000000000002)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ------------------------------------------------------------
-- 4) 菜单与权限点
--    type: 1目录 2菜单 3按钮
--    目录行 permission 为 NULL（MySQL 唯一索引允许多个 NULL，不会互相冲突）
--    component 对应 admin-web/src/views 下的 .vue 路径
-- ------------------------------------------------------------
INSERT INTO t_admin_menu
  (id, parent_id, name, type, path, component, icon, permission, sort, visible, status) VALUES
-- 数据看板
(9301,          0,     '数据看板',   2, '/dashboard',        'dashboard/index',      'Odometer',        'admin:dashboard:view',          100, 1, 1),
(930101,        9301,  '重算指标',   3, NULL,                NULL,                   NULL,              'admin:dashboard:rebuild',         0, 1, 1),
-- 内容管理
(9302,          0,     '内容管理',   1, '/content',          NULL,                   'Folder',          NULL,                              90, 1, 1),
(930201,        9302,  '帖子管理',   2, '/content/posts',    'content/PostList',     'Document',        'admin:content:post:list',         89, 1, 1),
(93020101,      930201,'查看详情',   3, NULL,                NULL,                   NULL,              'admin:content:post:detail',        0, 1, 1),
(93020102,      930201,'隐藏/恢复',  3, NULL,                NULL,                   NULL,              'admin:content:post:hide',          0, 1, 1),
(93020103,      930201,'删除',       3, NULL,                NULL,                   NULL,              'admin:content:post:delete',        0, 1, 1),
(930202,        9302,  '表白墙',     2, '/content/wall',     'content/WallList',     'ChatDotSquare',   'admin:content:wall:list',         88, 1, 1),
(93020201,      930202,'查看详情',   3, NULL,                NULL,                   NULL,              'admin:content:wall:detail',        0, 1, 1),
(93020202,      930202,'隐藏/恢复',  3, NULL,                NULL,                   NULL,              'admin:content:wall:hide',          0, 1, 1),
(93020203,      930202,'删除',       3, NULL,                NULL,                   NULL,              'admin:content:wall:delete',        0, 1, 1),
(930203,        9302,  '二手商品',   2, '/content/goods',    'content/GoodsList',    'Goods',           'admin:content:goods:list',        87, 1, 1),
(93020301,      930203,'查看详情',   3, NULL,                NULL,                   NULL,              'admin:content:goods:detail',       0, 1, 1),
(93020302,      930203,'强制下架',   3, NULL,                NULL,                   NULL,              'admin:content:goods:off-shelf',    0, 1, 1),
(930204,        9302,  '评论管理',   2, '/content/comments', 'content/CommentList',  'Comment',         'admin:content:comment:list',      86, 1, 1),
(93020401,      930204,'删除评论',   3, NULL,                NULL,                   NULL,              'admin:content:comment:delete',     0, 1, 1),
(930205,        9302,  '审核流水',   2, '/content/audits',   'content/AuditList',    'Tickets',         'admin:content:audit:list',        85, 1, 1),
-- 互动管理
(9303,          0,     '互动管理',   1, '/chat',             NULL,                   'ChatLineSquare',  NULL,                              80, 1, 1),
(930301,        9303,  '会话查询',   2, '/chat/conversations','chat/ConversationList','Connection',      'admin:chat:conversation:list',    79, 1, 1),
(930302,        9303,  '消息查询',   2, '/chat/messages',    'chat/MessageList',     'Message',         'admin:chat:message:list',         78, 1, 1),
(93030201,      930302,'全文检索',   3, NULL,                NULL,                   NULL,              'admin:chat:message:search',        0, 1, 1),
(93030202,      930302,'删除消息',   3, NULL,                NULL,                   NULL,              'admin:chat:message:delete',        0, 1, 1),
-- 用户管理
(9304,          0,     '用户管理',   1, '/user',             NULL,                   'User',            NULL,                              70, 1, 1),
(930401,        9304,  '用户列表',   2, '/user/list',        'user/UserList',        'UserFilled',      'admin:user:list',                 69, 1, 1),
(93040101,      930401,'查看详情',   3, NULL,                NULL,                   NULL,              'admin:user:detail',                0, 1, 1),
(93040102,      930401,'封禁/解封',  3, NULL,                NULL,                   NULL,              'admin:user:ban',                   0, 1, 1),
(93040103,      930401,'禁言/解禁',  3, NULL,                NULL,                   NULL,              'admin:user:mute',                  0, 1, 1),
(93040104,      930401,'拉黑/解除',  3, NULL,                NULL,                   NULL,              'admin:user:blacklist',             0, 1, 1),
(930402,        9304,  '实名审核',   2, '/user/verify',      'user/VerifyList',      'Postcard',        'admin:user:verify:list',          68, 1, 1),
(93040201,      930402,'审核处理',   3, NULL,                NULL,                   NULL,              'admin:user:verify',                0, 1, 1),
(930403,        9304,  '黑名单',     2, '/user/blacklist',   'user/Blacklist',       'CircleClose',     'admin:user:blacklist:list',       67, 1, 1),
(930404,        9304,  '禁言记录',   2, '/user/mutes',       'user/MuteList',        'Mute',            'admin:user:mute:list',            66, 1, 1),
-- 举报工单
(9305,          0,     '举报工单',   2, '/report',           'report/TicketList',    'Warning',         'admin:report:list',               60, 1, 1),
(930501,        9305,  '查看详情',   3, NULL,                NULL,                   NULL,              'admin:report:detail',              0, 1, 1),
(930502,        9305,  '受理/处理',  3, NULL,                NULL,                   NULL,              'admin:report:handle',              0, 1, 1),
-- 广告位管理
(9306,          0,     '广告位管理', 1, '/ad',               NULL,                   'Picture',         NULL,                              50, 1, 1),
(930601,        9306,  '广告位',     2, '/ad/slots',         'ad/SlotList',          'Grid',            'admin:ad:slot:list',              49, 1, 1),
(93060101,      930601,'新建',       3, NULL,                NULL,                   NULL,              'admin:ad:slot:create',             0, 1, 1),
(93060102,      930601,'编辑',       3, NULL,                NULL,                   NULL,              'admin:ad:slot:update',             0, 1, 1),
(93060103,      930601,'删除',       3, NULL,                NULL,                   NULL,              'admin:ad:slot:delete',             0, 1, 1),
(93060104,      930601,'启停',       3, NULL,                NULL,                   NULL,              'admin:ad:slot:status',             0, 1, 1),
(930602,        9306,  '素材投放',   2, '/ad/materials',     'ad/MaterialList',      'PictureFilled',   'admin:ad:material:list',          48, 1, 1),
(93060201,      930602,'新建',       3, NULL,                NULL,                   NULL,              'admin:ad:material:create',         0, 1, 1),
(93060202,      930602,'编辑',       3, NULL,                NULL,                   NULL,              'admin:ad:material:update',         0, 1, 1),
(93060203,      930602,'删除',       3, NULL,                NULL,                   NULL,              'admin:ad:material:delete',         0, 1, 1),
(93060204,      930602,'审核',       3, NULL,                NULL,                   NULL,              'admin:ad:material:audit',          0, 1, 1),
(93060205,      930602,'上下架',     3, NULL,                NULL,                   NULL,              'admin:ad:material:online',         0, 1, 1),
-- 通知公告
(9307,          0,     '通知公告',   2, '/notice',           'notice/NoticeList',    'Bell',            'admin:notice:list',               40, 1, 1),
(930701,        9307,  '新建',       3, NULL,                NULL,                   NULL,              'admin:notice:create',              0, 1, 1),
(930702,        9307,  '编辑',       3, NULL,                NULL,                   NULL,              'admin:notice:update',              0, 1, 1),
(930703,        9307,  '删除',       3, NULL,                NULL,                   NULL,              'admin:notice:delete',              0, 1, 1),
(930704,        9307,  '发布/下架',  3, NULL,                NULL,                   NULL,              'admin:notice:publish',             0, 1, 1),
-- 敏感词
(9308,          0,     '敏感词',     2, '/sensitive',        'sensitive/WordList',   'Filter',          'admin:sensitive:list',            30, 1, 1),
(930801,        9308,  '新建',       3, NULL,                NULL,                   NULL,              'admin:sensitive:create',           0, 1, 1),
(930802,        9308,  '编辑',       3, NULL,                NULL,                   NULL,              'admin:sensitive:update',           0, 1, 1),
(930803,        9308,  '删除',       3, NULL,                NULL,                   NULL,              'admin:sensitive:delete',           0, 1, 1),
(930804,        9308,  '刷新缓存',   3, NULL,                NULL,                   NULL,              'admin:sensitive:reload',           0, 1, 1),
-- 组织与权限
(9309,          0,     '组织与权限', 1, '/system',           NULL,                   'Setting',         NULL,                              20, 1, 1),
(930901,        9309,  '管理员',     2, '/system/admins',    'system/AdminList',     'Avatar',          'admin:admin:list',                19, 1, 1),
(93090101,      930901,'查看详情',   3, NULL,                NULL,                   NULL,              'admin:admin:detail',               0, 1, 1),
(93090102,      930901,'新建',       3, NULL,                NULL,                   NULL,              'admin:admin:create',               0, 1, 1),
(93090103,      930901,'编辑',       3, NULL,                NULL,                   NULL,              'admin:admin:update',               0, 1, 1),
(93090104,      930901,'删除',       3, NULL,                NULL,                   NULL,              'admin:admin:delete',               0, 1, 1),
(93090105,      930901,'启停',       3, NULL,                NULL,                   NULL,              'admin:admin:status',               0, 1, 1),
(93090106,      930901,'重置密码',   3, NULL,                NULL,                   NULL,              'admin:admin:reset-pwd',            0, 1, 1),
(930902,        9309,  '角色管理',   2, '/system/roles',     'system/RoleList',      'UserFilled',      'admin:role:list',                 18, 1, 1),
(93090201,      930902,'查看详情',   3, NULL,                NULL,                   NULL,              'admin:role:detail',                0, 1, 1),
(93090202,      930902,'新建',       3, NULL,                NULL,                   NULL,              'admin:role:create',                0, 1, 1),
(93090203,      930902,'编辑',       3, NULL,                NULL,                   NULL,              'admin:role:update',                0, 1, 1),
(93090204,      930902,'删除',       3, NULL,                NULL,                   NULL,              'admin:role:delete',                0, 1, 1),
(93090205,      930902,'分配权限',   3, NULL,                NULL,                   NULL,              'admin:role:grant',                 0, 1, 1),
(930903,        9309,  '菜单权限',   2, '/system/menus',     'system/MenuList',      'Menu',            'admin:menu:list',                 17, 1, 1),
(93090301,      930903,'新建',       3, NULL,                NULL,                   NULL,              'admin:menu:create',                0, 1, 1),
(93090302,      930903,'编辑',       3, NULL,                NULL,                   NULL,              'admin:menu:update',                0, 1, 1),
(93090303,      930903,'删除',       3, NULL,                NULL,                   NULL,              'admin:menu:delete',                0, 1, 1),
(930904,        9309,  '校区地区',   2, '/system/regions',   'system/RegionTree',    'OfficeBuilding',  'admin:region:list',               16, 1, 1),
(93090401,      930904,'新建',       3, NULL,                NULL,                   NULL,              'admin:region:create',              0, 1, 1),
(93090402,      930904,'编辑',       3, NULL,                NULL,                   NULL,              'admin:region:update',              0, 1, 1),
(93090403,      930904,'删除',       3, NULL,                NULL,                   NULL,              'admin:region:delete',              0, 1, 1),
(930905,        9309,  '负责人',     2, '/system/leaders',   'system/LeaderList',    'Stamp',           'admin:leader:list',               15, 1, 1),
(93090501,      930905,'指派',       3, NULL,                NULL,                   NULL,              'admin:leader:assign',              0, 1, 1),
(93090502,      930905,'撤销',       3, NULL,                NULL,                   NULL,              'admin:leader:revoke',              0, 1, 1),
-- 系统日志
(9310,          0,     '系统日志',   1, '/log',              NULL,                   'Document',        NULL,                              10, 1, 1),
(931001,        9310,  '操作日志',   2, '/log/operations',   'log/OperationLog',     'DocumentCopy',    'admin:log:operation:list',         9, 1, 1),
(93100101,      931001,'查看详情',   3, NULL,                NULL,                   NULL,              'admin:log:operation:detail',       0, 1, 1),
(931002,        9310,  '登录日志',   2, '/log/logins',       'log/LoginLog',         'Clock',           'admin:log:login:list',             8, 1, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  name      = VALUES(name),
  type      = VALUES(type),
  path      = VALUES(path),
  component = VALUES(component),
  icon      = VALUES(icon),
  sort      = VALUES(sort),
  visible   = VALUES(visible),
  status    = VALUES(status);

-- ------------------------------------------------------------
-- 5) 角色授权
--    用 INSERT IGNORE ... SELECT FROM t_admin_menu：
--    菜单表改了权限点字符串后，重跑本脚本能自动跟上新的权限点，
--    uk_role_menu 保证不会插重。超级管理员不需要种子数据
--    （拦截器对 super_flag=1 直接放行，菜单树也返回全量）。
--    id 用「角色专属基数 + menu_id」保证不同角色之间不冲突。
-- ------------------------------------------------------------

-- 内容审核员：内容管理全部 + 举报工单 + 互动查看/删除 + 用户查看 + 看板
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9400000000000000 + m.id, 910000000000000002, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission IS NOT NULL AND (
      m.permission LIKE 'admin:content:%'
   OR m.permission LIKE 'admin:report:%'
   OR m.permission LIKE 'admin:chat:%'
   OR m.permission IN ('admin:user:list', 'admin:user:detail', 'admin:dashboard:view')
);

-- 用户管理员：用户管理全部 + 互动管理全部 + 看板
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9500000000000000 + m.id, 910000000000000003, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission IS NOT NULL AND (
      m.permission LIKE 'admin:user:%'
   OR m.permission LIKE 'admin:chat:%'
   OR m.permission = 'admin:dashboard:view'
);

-- 运营：广告 + 公告 + 敏感词 + 看板（含重算）
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9600000000000000 + m.id, 910000000000000004, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission IS NOT NULL AND (
      m.permission LIKE 'admin:ad:%'
   OR m.permission LIKE 'admin:notice:%'
   OR m.permission LIKE 'admin:sensitive:%'
   OR m.permission LIKE 'admin:dashboard:%'
);

-- 校区负责人：只读为主（列表与详情），不做任何处置动作
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9700000000000000 + m.id, 910000000000000005, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission IS NOT NULL AND (
      m.permission IN (
        'admin:dashboard:view',
        'admin:content:post:list', 'admin:content:post:detail',
        'admin:content:wall:list', 'admin:content:wall:detail',
        'admin:content:goods:list', 'admin:content:goods:detail',
        'admin:content:audit:list',
        'admin:chat:conversation:list', 'admin:chat:message:list',
        'admin:user:list', 'admin:user:detail',
        'admin:report:list', 'admin:report:detail'
      )
);

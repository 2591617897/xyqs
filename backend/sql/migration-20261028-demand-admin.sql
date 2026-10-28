-- 后台菜单：需求广场（求物 / 求帮忙）
-- 2026-10-28
--
-- ⚠️ 执行时必须带 utf8mb4：mysql --default-character-set=utf8mb4 < 本文件
--    本文件已自带 SET NAMES utf8mb4 兜底 —— 之前用 Git Bash 的默认字符集执行，
--    中文菜单名被写坏成 16 字节 6 字（正确应为 12 字节 4 字），页面上显示成「闈?粖鍒楄〃」。
SET NAMES utf8mb4;
--
-- 权限点统一 admin:content:demand:*，挂在内容管理域下。
-- INSERT IGNORE + uk_permission 保证重复执行不会插重。
--
-- ⚠️ ID 必须避开已占用的 9301~9310（内容管理等）与 9320（校友群），
--    这里用 9330 段。菜单表 id 没有自增，撞了会被 INSERT IGNORE 静默丢弃，
--    后果是新菜单挂到别的目录下面。

INSERT IGNORE INTO t_admin_menu
  (id, parent_id, name, type, path, component, icon, permission, sort, visible, status) VALUES
(9330,      0,      '需求广场',     1, '/demand',            NULL,                    'ShoppingCart',  NULL,                            70, 1, 1),
(933001,    9330,   '需求列表',     2, '/demand/list',       'content/DemandList',    'List',          'admin:content:demand:list',     69, 1, 1),
(93300101,  933001, '查看详情',     3, NULL,                 NULL,                    NULL,            'admin:content:demand:detail',    0, 1, 1),
(93300102,  933001, '强制下架/恢复',3, NULL,                 NULL,                    NULL,            'admin:content:demand:off-shelf', 0, 1, 1),
(93300103,  933001, '删除需求',     3, NULL,                 NULL,                    NULL,            'admin:content:demand:delete',    0, 1, 1);

-- 用户管理员（910000000000000003）：需求管理全部
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9800000000000000 + m.id, 910000000000000003, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission LIKE 'admin:content:demand:%';

-- 内容审核员（910000000000000002）：需求处置全部（看 + 下架 + 删违规）
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9810000000000000 + m.id, 910000000000000002, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission LIKE 'admin:content:demand:%';

-- 超级管理员角色：一并授权，避免超管看不到新菜单。
-- ⚠️ t_admin_role 没有 is_super 列（超管标记在管理员用户侧），只能按 code 定位角色。
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9820000000000000 + m.id, r.id, m.id
FROM t_admin_role r
JOIN t_admin_menu m ON m.status = 1 AND m.permission LIKE 'admin:content:demand:%'
WHERE r.code = 'SUPER_ADMIN';

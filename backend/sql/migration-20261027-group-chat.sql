-- ============================================================
-- 校友群（WebSocket 实时群聊）—— 建表 + 后台菜单
--
-- 设计要点（详见 docs/校友群设计方案.md）：
--   1. 一所学校 = 一个群，biz_key 存 school_id 字符串，uk_type_key 兜并发建群
--   2. 未读用 last_read_id 游标，不给每条消息写万人已读行
--   3. @所有人 只更新 t_group.last_at_all_msg_id，零写放大
--   4. @指定人 才写 t_group_mention，单条消息最多 @ 10 人
--   5. 归档表去掉幂等唯一键（归档后不再接收写入）
--
-- 执行：mysql -u root -p guizhou_campus_fun < migration-20261027-group-chat.sql
-- 幂等：全部 CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行
-- ============================================================
USE guizhou_campus_fun;

-- ------------------------------------------------------------
-- 1) 群定义
--    type: 1 校友群（当前唯一）  2 同城群（预留，本轮不产出数据）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_group` (
  `id`                 BIGINT       NOT NULL COMMENT '雪花 id',
  `type`               TINYINT      NOT NULL DEFAULT 1 COMMENT '1 校友群 2 同城群（预留）',
  `biz_key`            VARCHAR(64)  NOT NULL COMMENT 'type=1 存 school_id 字符串',
  `name`               VARCHAR(64)  NOT NULL COMMENT '群名',
  `avatar`             VARCHAR(255) NOT NULL DEFAULT '' COMMENT '群头像',
  `notice`             VARCHAR(500) NOT NULL DEFAULT '' COMMENT '群公告',
  `school_id`          BIGINT       DEFAULT NULL COMMENT '冗余：所属学校',
  `member_count`       INT          NOT NULL DEFAULT 0 COMMENT '成员数冗余（只在入群/退群时增减）',
  `last_msg_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '最新消息 id，判红点用，避免 COUNT',
  `last_msg_text`      VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '最新消息摘要',
  `last_msg_time`      DATETIME     DEFAULT NULL COMMENT '最新消息时间',
  `last_at_all_msg_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '@所有人 的最新消息 id（不写万人行）',

  `rate_gap_sec`       INT          NOT NULL DEFAULT 0 COMMENT '发言间隔秒，0=按群规模自适应',
  `rate_day_max`       INT          NOT NULL DEFAULT 0 COMMENT '每日上限，0=按群规模自适应',
  `night_enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '夜间静音开关，后台可按群关',
  `night_start`        CHAR(5)      NOT NULL DEFAULT '00:30' COMMENT '夜间静音开始 HH:mm',
  `night_end`          CHAR(5)      NOT NULL DEFAULT '06:30' COMMENT '夜间静音结束 HH:mm',

  `status`             TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
  `parent_group_id`    BIGINT       DEFAULT NULL COMMENT '父群 id（未来拆子群预留，现恒 NULL）',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_key` (`type`, `biz_key`),
  KEY `idx_school` (`school_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群定义（校友群）';

-- ------------------------------------------------------------
-- 2) 群成员
--    notify_mode 默认 0（仅 @ 提醒）—— 万人群默认静默是核心产品决策
--    status: 1 在群 / 0 被踢 / 2 主动退群，后两者不参与自动入群
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_group_member` (
  `id`             BIGINT   NOT NULL,
  `group_id`       BIGINT   NOT NULL,
  `user_id`        BIGINT   NOT NULL,
  `role`           TINYINT  NOT NULL DEFAULT 0 COMMENT '0 普通成员 1 群管理员',
  `last_read_id`   BIGINT   NOT NULL DEFAULT 0 COMMENT '已读游标（群聊不逐条标已读）',
  `mention_unread` INT      NOT NULL DEFAULT 0 COMMENT '@我 的未读数（写时 +1，读时清零）',
  `notify_mode`    TINYINT  NOT NULL DEFAULT 0 COMMENT '0 仅@提醒（默认）1 全部 2 完全静音',
  `rules_agreed`   TINYINT  NOT NULL DEFAULT 0 COMMENT '是否已确认群规（首次入群引导）',
  `status`         TINYINT  NOT NULL DEFAULT 1 COMMENT '1 在群 0 被踢 2 主动退群',
  `join_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `leave_at`       DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员';

-- ------------------------------------------------------------
-- 3) 群消息
--    id 是雪花（时间有序），直接当增量游标用
--    client_msg_id 幂等：WS 断线重发 / 前端重试都会撞这个唯一键
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_group_message` (
  `id`            BIGINT        NOT NULL COMMENT '雪花 id，时间有序 → 增量游标',
  `group_id`      BIGINT        NOT NULL,
  `sender_id`     BIGINT        NOT NULL,
  `sender_name`   VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '昵称快照（展示前 refresh 为最新）',
  `sender_avatar` VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '头像快照（同上）',
  `type`          VARCHAR(16)   NOT NULL DEFAULT 'text' COMMENT 'text / image / system',
  `content`       VARCHAR(1000) NOT NULL,
  `at_type`       TINYINT       NOT NULL DEFAULT 0 COMMENT '0 无 1 @指定人 2 @所有人',
  `at_user_ids`   VARCHAR(512)  NOT NULL DEFAULT '' COMMENT 'JSON 数组，at_type=1 时，最多 10 人',
  `reply_to_id`   BIGINT        DEFAULT NULL COMMENT '被回复的消息 id，NULL = 不是回复',
  `reply_to_name` VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '被回复者昵称快照',
  `reply_to_text` VARCHAR(140)  NOT NULL DEFAULT '' COMMENT '被回复内容摘要快照',
  `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '1 撤回/被删（软删）',
  `deleted_by`    TINYINT       NOT NULL DEFAULT 0 COMMENT '删除来源 0未删除 1本人撤回 2管理员删除',
  `client_msg_id` VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '客户端幂等 id',
  `created_at`    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_group_id` (`group_id`, `id`),
  KEY `idx_group_atall` (`group_id`, `at_type`, `id`),
  KEY `idx_created` (`created_at`),
  UNIQUE KEY `uk_client` (`group_id`, `sender_id`, `client_msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息';

-- ------------------------------------------------------------
-- 4) @提醒（只给被 @ 的少数人写，绝不写全群）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_group_mention` (
  `id`         BIGINT   NOT NULL,
  `group_id`   BIGINT   NOT NULL,
  `message_id` BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL COMMENT '被 @ 的人',
  `read_flag`  TINYINT  NOT NULL DEFAULT 0 COMMENT '0 未读 1 已读',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_user` (`message_id`, `user_id`),
  KEY `idx_user_unread` (`user_id`, `read_flag`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='@提醒';

-- ------------------------------------------------------------
-- 5) 单人禁言
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_group_mute` (
  `id`          BIGINT       NOT NULL,
  `group_id`    BIGINT       NOT NULL,
  `user_id`     BIGINT       NOT NULL,
  `start_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_at`      DATETIME     DEFAULT NULL COMMENT 'NULL = 永久',
  `reason`      VARCHAR(128) NOT NULL DEFAULT '',
  `operator_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '0 = 系统',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 生效 0 已解除',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_group_user_status` (`group_id`, `user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群禁言记录';

-- ------------------------------------------------------------
-- 6) 归档表（结构与热表一致，去掉幂等唯一键）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_group_message_archive` (
  `id`            BIGINT        NOT NULL,
  `group_id`      BIGINT        NOT NULL,
  `sender_id`     BIGINT        NOT NULL,
  `sender_name`   VARCHAR(32)   NOT NULL DEFAULT '',
  `sender_avatar` VARCHAR(255)  NOT NULL DEFAULT '',
  `type`          VARCHAR(16)   NOT NULL DEFAULT 'text',
  `content`       VARCHAR(1000) NOT NULL,
  `at_type`       TINYINT       NOT NULL DEFAULT 0,
  `at_user_ids`   VARCHAR(512)  NOT NULL DEFAULT '',
  `reply_to_id`   BIGINT        DEFAULT NULL,
  `reply_to_name` VARCHAR(32)   NOT NULL DEFAULT '',
  `reply_to_text` VARCHAR(140)  NOT NULL DEFAULT '',
  `deleted`       TINYINT       NOT NULL DEFAULT 0,
  `deleted_by`    TINYINT       NOT NULL DEFAULT 0,
  `created_at`    DATETIME(3)   NOT NULL,
  `archived_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_group_id` (`group_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群消息归档（12 个月前）';

-- ------------------------------------------------------------
-- 7) 后台菜单：校友群管理
--    权限点统一 admin:group:*
--    INSERT IGNORE + uk_permission 保证重复执行不会插重
-- ------------------------------------------------------------
-- ⚠️ ID 必须避开已被占用的 9301~9310（含「系统日志」9310 与其子菜单 931001/93100101），
--    这里用 9320 段。菜单表 id 没有自增，撞了就会被 INSERT IGNORE 静默丢弃，
--    后果是新菜单挂到别的目录下面（第一次写这个脚本就踩过一次）。
INSERT IGNORE INTO t_admin_menu
  (id, parent_id, name, type, path, component, icon, permission, sort, visible, status) VALUES
(9320,      0,     '校友群',     1, '/group',              NULL,                    'ChatSquare',    NULL,                        77, 1, 1),
(932001,    9320,  '群列表',     2, '/group/list',         'group/GroupList',       'List',          'admin:group:list',          76, 1, 1),
(93200101,  932001,'查看详情',   3, NULL,                  NULL,                    NULL,            'admin:group:detail',         0, 1, 1),
(93200102,  932001,'修改群配置', 3, NULL,                  NULL,                    NULL,            'admin:group:update',         0, 1, 1),
(93200103,  932001,'成员列表',   3, NULL,                  NULL,                    NULL,            'admin:group:member:list',    0, 1, 1),
(93200104,  932001,'设置管理员', 3, NULL,                  NULL,                    NULL,            'admin:group:member:role',    0, 1, 1),
(93200105,  932001,'禁言/解禁',  3, NULL,                  NULL,                    NULL,            'admin:group:member:mute',    0, 1, 1),
(93200106,  932001,'踢出成员',   3, NULL,                  NULL,                    NULL,            'admin:group:member:kick',    0, 1, 1),
(93200107,  932001,'消息列表',   3, NULL,                  NULL,                    NULL,            'admin:group:message:list',   0, 1, 1),
(93200108,  932001,'删除消息',   3, NULL,                  NULL,                    NULL,            'admin:group:message:delete', 0, 1, 1);

-- 用户管理员（910000000000000003）：群管理全部
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9800000000000000 + m.id, 910000000000000003, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission LIKE 'admin:group:%';

-- 内容审核员（910000000000000002）：只看 + 删违规消息，不给配置与踢人
INSERT IGNORE INTO t_admin_role_menu (id, role_id, menu_id)
SELECT 9900000000000000 + m.id, 910000000000000002, m.id
FROM t_admin_menu m
WHERE m.status = 1 AND m.permission IN (
  'admin:group:list', 'admin:group:detail',
  'admin:group:member:list',
  'admin:group:message:list', 'admin:group:message:delete'
);

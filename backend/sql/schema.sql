-- ============================================================
-- 校园趣事 · 数据库脚本 (MySQL 8.0)
-- 说明：各微服务共用一套库便于本地开发；生产建议按服务拆库
-- ============================================================
CREATE DATABASE IF NOT EXISTS guizhou_campus_fun DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE guizhou_campus_fun;

-- 学校
CREATE TABLE IF NOT EXISTS t_school (
  id          BIGINT       NOT NULL PRIMARY KEY,
  name        VARCHAR(64)  NOT NULL COMMENT '学校全称',
  province    VARCHAR(32)  DEFAULT NULL,
  city        VARCHAR(32)  DEFAULT NULL,
  logo        VARCHAR(255) DEFAULT NULL,
  sort        INT          NOT NULL DEFAULT 0,
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_name (name)
) COMMENT '学校字典（身份认证必选）';

-- 用户（身份认证为核心字段）
CREATE TABLE IF NOT EXISTS t_user (
  id            BIGINT       NOT NULL PRIMARY KEY,
  platform      VARCHAR(16)  NOT NULL DEFAULT 'wx' COMMENT '宿主平台 wx / qq',
  openid        VARCHAR(64)  NOT NULL COMMENT '宿主平台内唯一',
  unionid       VARCHAR(64)  DEFAULT NULL,
  nickname      VARCHAR(64)  DEFAULT NULL,
  avatar        VARCHAR(255) DEFAULT NULL,
  gender        TINYINT      NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  phone         VARCHAR(20)  DEFAULT NULL,
  school_id     BIGINT       DEFAULT NULL COMMENT '所属学校，注册时选择',
  student_no    VARCHAR(32)  DEFAULT NULL COMMENT '学号',
  real_name     VARCHAR(32)  DEFAULT NULL COMMENT '真实姓名',
  verify_status TINYINT      NOT NULL DEFAULT 0 COMMENT '0未认证 1审核中 2已认证 3驳回',
  verify_img    VARCHAR(255) DEFAULT NULL COMMENT '学生证/校园卡照片',
  verify_remark VARCHAR(255) DEFAULT NULL COMMENT '驳回原因',
  bio           VARCHAR(255) DEFAULT NULL,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0封禁',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_platform_openid (platform, openid),
  KEY idx_school (school_id, verify_status)
) COMMENT '用户';

-- 二手商品
CREATE TABLE IF NOT EXISTS t_goods (
  id             BIGINT        NOT NULL PRIMARY KEY,
  user_id        BIGINT        NOT NULL COMMENT '卖家',
  school_id      BIGINT        DEFAULT NULL,
  seller_name    VARCHAR(64)   DEFAULT NULL COMMENT '卖家昵称快照(读模型冗余)',
  seller_avatar  VARCHAR(255)  DEFAULT NULL,
  title          VARCHAR(80)   NOT NULL,
  description    VARCHAR(2000) DEFAULT NULL,
  price          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  original_price DECIMAL(10,2) DEFAULT NULL,
  category_id    INT           NOT NULL DEFAULT 0 COMMENT '1数码 2图书 3生活 4服饰 5运动 6其他',
  condition_level TINYINT      DEFAULT NULL COMMENT '成色: 1全新 2几乎全新 3轻微使用痕迹 4明显使用痕迹',
  purchase_time  VARCHAR(20)   DEFAULT NULL COMMENT '入手时间, 如: 2023年9月',
  usage_frequency VARCHAR(20)  DEFAULT NULL COMMENT '使用频率: 每天使用/经常使用/偶尔使用/几乎没用过',
  source         VARCHAR(20)   DEFAULT NULL COMMENT '来源渠道: 自购自用/朋友赠送/活动奖品/实验室配发/其他',
  warranty       VARCHAR(20)   DEFAULT NULL COMMENT '保修状态: 官方保修中/官方保修已过/不确定',
  support_trial  TINYINT       NOT NULL DEFAULT 1 COMMENT '是否支持面交试机 0否 1是',
  images         VARCHAR(1000) DEFAULT NULL COMMENT '逗号分隔图片地址',
  trade_type     TINYINT       NOT NULL DEFAULT 1 COMMENT '1校内自提 2邮寄',
  location       VARCHAR(64)   DEFAULT NULL COMMENT '交易地点，如：南苑食堂门口',
  status         TINYINT       NOT NULL DEFAULT 1 COMMENT '0已下架 1在售 2已售出',
  view_count     INT           NOT NULL DEFAULT 0,
  like_count     INT           NOT NULL DEFAULT 0,
  created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_school_status (school_id, status, created_at),
  KEY idx_user (user_id, status)
) COMMENT '二手商品';

-- 购买意向 / 交易单
CREATE TABLE IF NOT EXISTS t_goods_order (
  id         BIGINT       NOT NULL PRIMARY KEY,
  goods_id   BIGINT       NOT NULL,
  buyer_id   BIGINT       NOT NULL,
  seller_id  BIGINT       NOT NULL,
  buyer_name VARCHAR(64)  DEFAULT NULL,
  message    VARCHAR(255) DEFAULT NULL COMMENT '买家留言',
  contact    VARCHAR(64)  DEFAULT NULL COMMENT '联系方式(QQ/微信/手机)',
  status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0待卖家确认 1已确认 2已完成 3已取消',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_goods (goods_id),
  KEY idx_buyer (buyer_id),
  KEY idx_seller (seller_id)
) COMMENT '购买意向单';

-- 通用点赞表（target_type: GOODS / WALL / POST）
CREATE TABLE IF NOT EXISTS t_like (
  id          BIGINT      NOT NULL PRIMARY KEY,
  target_type VARCHAR(16) NOT NULL,
  target_id   BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_target_user (target_type, target_id, user_id)
) COMMENT '通用点赞/收藏';

-- 匿名表白墙
CREATE TABLE IF NOT EXISTS t_wall_post (
  id            BIGINT       NOT NULL PRIMARY KEY,
  user_id       BIGINT       NOT NULL COMMENT '内部留存，仅用于风控，不对外返回',
  school_id     BIGINT       DEFAULT NULL,
  anon_name     VARCHAR(32)  NOT NULL COMMENT '匿名昵称，如：匿名同学·037',
  anon_avatar   TINYINT      NOT NULL DEFAULT 1 COMMENT '匿名头像序号 1-8',
  content       VARCHAR(1000) NOT NULL,
  images        VARCHAR(1000) DEFAULT NULL,
  category      VARCHAR(16)  NOT NULL DEFAULT 'love' COMMENT '分类：love精选表白/tree树洞吐槽/find寻物启事/findp捞人',
  like_count    INT          NOT NULL DEFAULT 0,
  comment_count INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0被隐藏',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_school (school_id, status, created_at)
) COMMENT '表白墙帖子';

CREATE TABLE IF NOT EXISTS t_wall_comment (
  id         BIGINT       NOT NULL PRIMARY KEY,
  post_id    BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  anon_name  VARCHAR(32)  NOT NULL,
  content    VARCHAR(500) NOT NULL,
  images     VARCHAR(1000)         DEFAULT NULL COMMENT '逗号分隔，最多 3 张',
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父评论id，0=直接评论；>0=对该评论的回复',
  reply_to_name VARCHAR(32) DEFAULT NULL COMMENT '被回复人匿名昵称快照',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_post (post_id)
) COMMENT '表白墙评论';

-- 频道（类似 QQ 频道分区）
CREATE TABLE IF NOT EXISTS t_channel (
  id          BIGINT       NOT NULL PRIMARY KEY,
  name        VARCHAR(32)  NOT NULL,
  icon        VARCHAR(16)  DEFAULT NULL COMMENT 'emoji 图标',
  description VARCHAR(255) DEFAULT NULL,
  school_id   BIGINT       DEFAULT NULL COMMENT 'NULL 表示全平台频道',
  sort        INT          NOT NULL DEFAULT 0,
  post_count  INT          NOT NULL DEFAULT 0,
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
) COMMENT '频道';

CREATE TABLE IF NOT EXISTS t_post (
  id            BIGINT       NOT NULL PRIMARY KEY,
  channel_id    BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  school_id     BIGINT       DEFAULT NULL,
  author_name   VARCHAR(64)  DEFAULT NULL COMMENT '昵称快照',
  author_avatar VARCHAR(255) DEFAULT NULL,
  title         VARCHAR(80)  NOT NULL,
  content       VARCHAR(3000) NOT NULL,
  images        VARCHAR(1500) DEFAULT NULL,
  like_count    INT          NOT NULL DEFAULT 0,
  comment_count INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0删除/隐藏',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_channel (channel_id, status, created_at),
  KEY idx_user (user_id)
) COMMENT '频道帖子';

CREATE TABLE IF NOT EXISTS t_post_comment (
  id         BIGINT       NOT NULL PRIMARY KEY,
  post_id    BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  user_name  VARCHAR(64)  DEFAULT NULL,
  user_avatar VARCHAR(255) DEFAULT NULL,
  content    VARCHAR(500) NOT NULL DEFAULT '',
  images     VARCHAR(1000)         DEFAULT NULL COMMENT '逗号分隔，最多 3 张',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_post (post_id)
) COMMENT '帖子评论';

-- ============================================================
-- 升级脚本：已建过的库补充后加的字段
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行报 duplicate column 可忽略
-- ============================================================
-- ALTER TABLE t_post_comment ADD COLUMN images VARCHAR(1000) DEFAULT NULL COMMENT '逗号分隔，最多 3 张' AFTER content;
-- ALTER TABLE t_wall_comment ADD COLUMN images VARCHAR(1000) DEFAULT NULL COMMENT '逗号分隔，最多 3 张' AFTER content;

-- ------------------------------------------------------------
-- 迁移到微信小程序：t_user 增加 platform 维度
-- 历史数据来自 QQ 小程序，默认值给 'qq'（不是 'wx'，否则老用户会被标成微信用户）
-- 执行后新旧用户各自按 (platform, openid) 定位，同一个人两端登录是两个账号
-- ------------------------------------------------------------
-- ALTER TABLE t_user ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'qq' COMMENT '宿主平台 wx / qq' AFTER id;

-- ------------------------------------------------------------
-- 私信（campus-message 服务）
-- ------------------------------------------------------------
-- 会话：固定把较小的 user_id 放 user_a，较大的放 user_b，
-- 保证同一对用户的会话只有一行，双向查询无需 or 条件。
-- goods_id 用 0 表示「非商品会话」，因此唯一键不会出现 NULL 重复问题。
CREATE TABLE IF NOT EXISTS t_message_conversation (
  id            BIGINT        NOT NULL PRIMARY KEY,
  user_a        BIGINT        NOT NULL COMMENT '会话成员 A（user_id 较小的一方）',
  user_b        BIGINT        NOT NULL COMMENT '会话成员 B（user_id 较大的一方）',
  initiator_id  BIGINT        NOT NULL COMMENT '会话发起人（用于判断买卖角色）',
  goods_id      BIGINT        NOT NULL DEFAULT 0 COMMENT '关联商品，0 表示非商品会话',
  goods_title   VARCHAR(120)  DEFAULT NULL COMMENT '商品标题快照（聊天头部卡片展示）',
  goods_price   DECIMAL(10,2) DEFAULT NULL COMMENT '商品价格快照',
  goods_emoji   VARCHAR(16)   DEFAULT NULL COMMENT '商品分类 emoji 快照',
  goods_status  TINYINT       DEFAULT NULL COMMENT '商品状态快照 0已下架 1在售 2已售出',
  anon          TINYINT       NOT NULL DEFAULT 0 COMMENT '1 匿名会话（表白墙回声），peer 不展示真实身份',
  anon_name     VARCHAR(64)   DEFAULT NULL COMMENT '匿名会话的展示名快照',
  last_msg_id   BIGINT        DEFAULT NULL COMMENT '最后一条消息 id',
  last_content  VARCHAR(255)  DEFAULT NULL COMMENT '最后一条消息摘要',
  last_time     DATETIME      DEFAULT NULL COMMENT '最后一条消息时间',
  unread_a      INT           NOT NULL DEFAULT 0 COMMENT 'A 的未读数',
  unread_b      INT           NOT NULL DEFAULT 0 COMMENT 'B 的未读数',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0已删除',
  created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pair_goods (user_a, user_b, goods_id),
  KEY idx_a (user_a, status, last_time),
  KEY idx_b (user_b, status, last_time)
) COMMENT '私信会话';

-- 消息
CREATE TABLE IF NOT EXISTS t_message (
  id              BIGINT        NOT NULL PRIMARY KEY,
  conversation_id BIGINT        NOT NULL,
  sender_id       BIGINT        NOT NULL,
  receiver_id     BIGINT        NOT NULL,
  type            VARCHAR(16)   NOT NULL DEFAULT 'text' COMMENT 'text 文本 / location 定位 / image 图片 / system 系统',
  content         VARCHAR(2000) NOT NULL,
  read_flag       TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  KEY idx_conversation (conversation_id, id),
  KEY idx_receiver (receiver_id, read_flag)
) COMMENT '私信消息';
-- ALTER TABLE t_user DROP INDEX uk_openid;
-- ALTER TABLE t_user ADD UNIQUE KEY uk_platform_openid (platform, openid);

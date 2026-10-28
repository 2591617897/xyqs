-- 修复 t_demand / t_demand_reply 被写坏的中文注释
-- 2026-10-28
--
-- 起因：首次建表用 Git Bash 的 mysql < file.sql 执行，客户端默认字符集不是 utf8mb4，
--       中文注释被按其它编码解释后落库（表结构本身没问题，只是注释乱码）。
-- 修法：只 MODIFY COMMENT，不动列类型，也不动数据（t_demand 已有真实数据，不能删表重建）。
--
-- 执行：mysql --default-character-set=utf8mb4 < 本文件
SET NAMES utf8mb4;

ALTER TABLE t_demand COMMENT = '校园需求(求物/求帮忙)';

ALTER TABLE t_demand
  MODIFY COLUMN user_id        BIGINT        NOT NULL COMMENT '发布者',
  MODIFY COLUMN author_name    VARCHAR(64)   DEFAULT NULL COMMENT '发布者昵称快照(读模型冗余)',
  MODIFY COLUMN type           TINYINT       NOT NULL DEFAULT 1 COMMENT '1求物 2求帮忙',
  MODIFY COLUMN title          VARCHAR(60)   NOT NULL COMMENT '想要什么 / 想做什么',
  MODIFY COLUMN description    VARCHAR(2000) DEFAULT NULL COMMENT '具体要求',
  MODIFY COLUMN category_id    INT           NOT NULL DEFAULT 0 COMMENT '需求分类, 见 DemandService.DEMAND_CATEGORIES',
  MODIFY COLUMN budget         DECIMAL(10,2) DEFAULT NULL COMMENT '预算/报酬; NULL=面议',
  MODIFY COLUMN expect_time    VARCHAR(32)   DEFAULT NULL COMMENT '期望时间, 如: 本周内 / 今天下午',
  MODIFY COLUMN location       VARCHAR(64)   DEFAULT NULL COMMENT '交接地点, 如: 南苑食堂门口',
  MODIFY COLUMN images         VARCHAR(1000) DEFAULT NULL COMMENT '参考图, 逗号分隔',
  MODIFY COLUMN visibility     VARCHAR(16)   NOT NULL DEFAULT 'school' COMMENT 'school仅本校 / city同市',
  MODIFY COLUMN status         TINYINT       NOT NULL DEFAULT 1 COMMENT '0已下架 1进行中 2已完成',
  MODIFY COLUMN reply_count    INT           NOT NULL DEFAULT 0 COMMENT '回应数(含已忽略)';

ALTER TABLE t_demand_reply COMMENT = '需求回应';

ALTER TABLE t_demand_reply
  MODIFY COLUMN demand_id      BIGINT       NOT NULL,
  MODIFY COLUMN replier_id     BIGINT       NOT NULL COMMENT '回应者',
  MODIFY COLUMN replier_name   VARCHAR(64)  DEFAULT NULL COMMENT '回应者昵称快照',
  MODIFY COLUMN message        VARCHAR(500) DEFAULT NULL COMMENT '我能提供什么',
  MODIFY COLUMN contact        VARCHAR(255) DEFAULT NULL COMMENT '联系方式(QQ/微信/手机), AES 加密存储',
  MODIFY COLUMN status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待查看 1已读 2已忽略';

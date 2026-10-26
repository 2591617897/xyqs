-- ============================================================
-- 广告位定向支持多选（2026-10-27）
--
-- 背景：后台「投放范围=城市/校区」原本是两下手填输入框 ——
--   city      填「贵阳」（还得记住不能带「市」字）
--   school_id 填 t_school.id（19 位雪花值，肉眼核对不了）
-- 填错一个字符，C 端就永远匹配不上，但后台显示「已上架」，
-- 两端都不报错，属于最难定位的一类问题。
--
-- 改法：改成多选下拉（选项来自 t_school 的真实数据），
-- 存储随之从「单值」变成「逗号分隔多值」：
--   city       varchar(32)  → varchar(255)
--   school_id  bigint       → school_ids varchar(255)
--
-- 为什么是逗号分隔而不是关联表：
--   t_ad_slot 是配置表（个位数行），配一条广告位才写一次，
--   FIND_IN_SET 的全表扫描代价可以忽略；换来的是 C 端查询
--   仍然是一条 JOIN，不用引入子表维护与事务一致性。
--   值只可能是中文城市名或纯数字 id，天然不含逗号。
--
-- ⚠️ 本脚本不可重复执行（ALTER 不幂等），只跑一次。
-- ============================================================

ALTER TABLE t_ad_slot
    MODIFY COLUMN city VARCHAR(255) DEFAULT NULL
        COMMENT 'scope_type=2 时的城市，逗号分隔（如 贵阳,遵义）';

ALTER TABLE t_ad_slot
    CHANGE COLUMN school_id school_ids VARCHAR(255) DEFAULT NULL
        COMMENT 'scope_type=3 时的校区ID，逗号分隔（如 1001,1002）';

-- 原索引 (scope_type, city, school_id) 建立在单值列上，
-- 改多值后 FIND_IN_SET 用不到它，留着只会误导后来人。
ALTER TABLE t_ad_slot DROP INDEX idx_scope;
ALTER TABLE t_ad_slot ADD KEY idx_scope_type (scope_type);

-- 存量数据校验：单值本身就是合法的「只有一个元素的多值」，无需转换。
SELECT id, code, scope_type, city, school_ids FROM t_ad_slot ORDER BY position;

-- ============================================================================
-- 广告位展示规格（2026-10-27）
--
-- 背景：尺寸原来是 t_ad_slot.size_desc 自由文本，后台能填「800x600」，
-- 而小程序广告容器高度是写死的（首页 300rpx / 列表 240 / 个人中心 200），
-- 素材按 aspectFill 填充 → 实际被裁掉一截，两端都以为自己是对的。
--
-- 现在尺寸收敛成规格编码（campus-common 的 AdSizeCatalog），由后台从下拉里选：
--   banner_300 750×300（首页大图）
--   banner_240 750×240（列表顶部，兜底）
--   banner_200 750×200（个人中心）
--   banner_160 750×160（细条）
-- 小程序按接口下发的 displayHeight 渲染，size_desc 退化为派生展示文案。
--
-- 幂等：可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- 1) 新增规格列
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_ad_slot'
       AND COLUMN_NAME = 'size_code'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE t_ad_slot ADD COLUMN size_code VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''展示规格编码（AdSizeCatalog），决定小程序渲染高度'' AFTER size_desc',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 历史数据回填：按广告位编码映射到它原本的渲染高度
UPDATE t_ad_slot SET size_code = 'banner_300' WHERE size_code = '' AND code = 'home_banner';
UPDATE t_ad_slot SET size_code = 'banner_200' WHERE size_code = '' AND code = 'mine_banner';
UPDATE t_ad_slot SET size_code = 'banner_240'
 WHERE size_code = '' AND code IN ('zone_top', 'wall_top', 'goods_top', 'splash');
-- 未知编码（历史脏数据）：按兜底规格，避免渲染成 0 高度
UPDATE t_ad_slot SET size_code = 'banner_240' WHERE size_code = '';

-- 3) size_desc 改成由 size_code 派生的文案（覆盖手填的「750x300」这类自由文本）
UPDATE t_ad_slot SET size_desc = '750×300 · 2.5:1 · 建议 1080×432' WHERE size_code = 'banner_300';
UPDATE t_ad_slot SET size_desc = '750×240 · 3.13:1 · 建议 1080×346' WHERE size_code = 'banner_240';
UPDATE t_ad_slot SET size_desc = '750×200 · 3.75:1 · 建议 1080×288' WHERE size_code = 'banner_200';
UPDATE t_ad_slot SET size_desc = '750×160 · 4.69:1 · 建议 1080×230' WHERE size_code = 'banner_160';

-- 4) 核对
SELECT id, code, name, size_code, size_desc, status FROM t_ad_slot ORDER BY position;

-- 运营位演示数据：首页 Banner 广告位 + 素材 + 公告
-- 用途：验证「后台配置 → 小程序可读取」这条链路是否真的打通。
-- 不需要时用最下面的 DELETE 语句整体移除即可。

USE guizhou_campus_fun;

-- 1) 首页广告位（code 是小程序拉取用的唯一标识）
INSERT INTO t_ad_slot
  (id, name, code, position, scope_type, city, school_id, size_desc, max_count, sort, status, audit_status, start_at, end_at, remark)
VALUES
  (930000000000000001, '首页Banner', 'home_banner', 1, 1, NULL, NULL, '750x300', 3, 100, 1, 1, NULL, NULL, '演示数据：首页顶部轮播')
ON DUPLICATE KEY UPDATE
  name = VALUES(name), max_count = VALUES(max_count), status = VALUES(status), audit_status = VALUES(audit_status);

-- 2) 两条素材：一条跳二手广场页面，一条跳指定商品
INSERT INTO t_ad_material
  (id, slot_id, slot_name, title, image, link_type, link_value, sort, status, audit_status, start_at, end_at, remark)
VALUES
  (931000000000000001, 930000000000000001, '首页Banner', '毕业季二手书专场',
   'http://127.0.0.1:8888/uploads/2026/10/acfafa4f3a7e4d98b81a9d57443a4d8a.jpg',
   4, '/pages/goods/list', 100, 1, 1, NULL, NULL, '演示数据'),
  (931000000000000002, 930000000000000001, '首页Banner', '校园闲置自由交易',
   'http://127.0.0.1:8888/uploads/2026/10/be67980fa0424072bfdaf947d9f6dfb0.jpg',
   4, '/pages/goods/list', 90, 1, 1, NULL, NULL, '演示数据')
ON DUPLICATE KEY UPDATE
  title = VALUES(title), image = VALUES(image), status = VALUES(status), audit_status = VALUES(audit_status);

-- 3) 公告：target_type=2 表示「小程序全体可见」（1 是仅后台管理员，绝不能推给小程序）
INSERT INTO t_admin_notice
  (id, title, content, type, target_type, school_id, level, status, publish_at, offline_at, publisher_id, publisher_name)
VALUES
  (932000000000000001, '校园聚社区规范上线啦', '为了维护良好的校园社区氛围，我们上线了社区规范。请大家文明发言，友善交流。',
   1, 2, NULL, 2, 1, NOW(), NULL, 900000000000000001, 'admin'),
  (932000000000000002, '毕业季二手集市火热进行中', '又到一年毕业季，闲置好物别浪费，快来二手广场发布你的宝贝吧。',
   2, 2, NULL, 1, 1, NOW(), NULL, 900000000000000001, 'admin')
ON DUPLICATE KEY UPDATE
  title = VALUES(title), content = VALUES(content), status = VALUES(status), target_type = VALUES(target_type);

-- 移除演示数据（需要时执行）：
-- DELETE FROM t_ad_material WHERE id IN (931000000000000001, 931000000000000002);
-- DELETE FROM t_ad_slot WHERE id = 930000000000000001;
-- DELETE FROM t_admin_notice WHERE id IN (932000000000000001, 932000000000000002);

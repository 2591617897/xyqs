-- 初始化数据
USE guizhou_campus_fun;

INSERT INTO t_school (id, name, province, city, sort) VALUES
(1001, '清华大学', '北京', '北京', 100),
(1002, '北京大学', '北京', '北京', 99),
(1003, '复旦大学', '上海', '上海', 98),
(1004, '上海交通大学', '上海', '上海', 97),
(1005, '浙江大学', '浙江', '杭州', 96),
(1006, '武汉大学', '湖北', '武汉', 95),
(1007, '四川大学', '四川', '成都', 94),
(1008, '中山大学', '广东', '广州', 93),
(1009, '华中科技大学', '湖北', '武汉', 92),
(1010, '南京大学', '江苏', '南京', 91),
(1011, '西安交通大学', '陕西', '西安', 90),
(1012, '哈尔滨工业大学', '黑龙江', '哈尔滨', 89),
(1013, '山东大学', '山东', '济南', 88),
(1014, '厦门大学', '福建', '厦门', 87),
(1015, '湖南大学', '湖南', '长沙', 86),
(1016, '郑州大学', '河南', '郑州', 85),
(1017, '深圳大学', '广东', '深圳', 84),
(1018, '苏州大学', '江苏', '苏州', 83),
(1019, '西南大学', '重庆', '重庆', 82),
(1020, '兰州大学', '甘肃', '兰州', 81)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO t_channel (id, name, icon, description, school_id, sort) VALUES
(2001, '校园公告', '📢', '失物招领、校园通知、社团招新', NULL, 100),
(2002, '学习交流', '📚', '选课攻略、考研保研、资料互换', NULL, 90),
(2003, '吃喝玩乐', '🍜', '食堂测评、周边探店、周末去哪儿', NULL, 80),
(2004, '树洞吐槽', '🕳️', '今天也想碎碎念一下', NULL, 70),
(2005, '二手交易', '♻️', '闲置好物流转，低碳又省钱', NULL, 60),
(2006, '运动搭子', '🏀', '篮球、羽毛球、夜跑约起来', NULL, 50)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 演示用户（已通过身份认证）
INSERT INTO t_user (id, openid, nickname, avatar, gender, school_id, student_no, real_name, verify_status, bio)
VALUES (9001, 'demo_openid_001', '林小满', '', 2, 1005, '2022010101', '林满', 2, '浙大在读，喜欢逛食堂和拍照')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

INSERT INTO t_goods (id, user_id, school_id, seller_name, title, description, price, original_price, category_id, trade_type, location, status, view_count)
VALUES
(8001, 9001, 1005, '林小满', '九成新机械键盘 87 键', '去年双十一买的，青轴，手感很好。因为换静音轴了所以出，配件齐全，可当面验货。', 129.00, 299.00, 1, 1, '紫金港校区 · 二食堂门口', 1, 56),
(8002, 9001, 1005, '林小满', '考研数学全套资料', '张宇基础30讲 + 1000题 + 真题，笔记很全，几乎没折角。适合刚开始准备的同学。', 45.00, 180.00, 2, 1, '紫金港校区 · 图书馆', 1, 32)
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO t_wall_post (id, user_id, school_id, anon_name, anon_avatar, content, like_count)
VALUES
(7001, 9001, 1005, '匿名同学·037', 3, '图书馆四楼靠窗那个总穿灰色卫衣的男生，你昨天帮我把掉在地上的书捡起来了，一直没来得及说谢谢。', 24)
ON DUPLICATE KEY UPDATE content = VALUES(content);

INSERT INTO t_post (id, channel_id, user_id, school_id, author_name, title, content, like_count)
VALUES
(6001, 2003, 9001, 1005, '林小满', '紫金港食堂测评：这 5 个窗口闭眼入', '排名分先后：1. 三食堂麻辣香锅 2. 二食堂现烤面包 3. 一食堂手工水饺……', 18)
ON DUPLICATE KEY UPDATE title = VALUES(title);

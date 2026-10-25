-- 初始化数据
USE guizhou_campus_fun;

INSERT INTO t_school (id, name, province, city, district, sort) VALUES
(1001, '清华大学', '北京', '北京', '海淀区', 100),
(1002, '北京大学', '北京', '北京', '海淀区', 99),
(1003, '复旦大学', '上海', '上海', '杨浦区', 98),
(1004, '上海交通大学', '上海', '上海', '闵行区', 97),
(1005, '浙江大学', '浙江', '杭州', '西湖区', 96),
(1006, '武汉大学', '湖北', '武汉', '武昌区', 95),
(1007, '四川大学', '四川', '成都', '武侯区', 94),
(1008, '中山大学', '广东', '广州', '海珠区', 93),
(1009, '华中科技大学', '湖北', '武汉', '洪山区', 92),
(1010, '南京大学', '江苏', '南京', '鼓楼区', 91),
(1011, '西安交通大学', '陕西', '西安', '碑林区', 90),
(1012, '哈尔滨工业大学', '黑龙江', '哈尔滨', '南岗区', 89),
(1013, '山东大学', '山东', '济南', '历城区', 88),
(1014, '厦门大学', '福建', '厦门', '思明区', 87),
(1015, '湖南大学', '湖南', '长沙', '岳麓区', 86),
(1016, '郑州大学', '河南', '郑州', '高新区', 85),
(1017, '深圳大学', '广东', '深圳', '南山区', 84),
(1018, '苏州大学', '江苏', '苏州', '姑苏区', 83),
(1019, '西南大学', '重庆', '重庆', '北碚区', 82),
(1020, '兰州大学', '甘肃', '兰州', '城关区', 81)
ON DUPLICATE KEY UPDATE name = VALUES(name), province = VALUES(province), city = VALUES(city), district = VALUES(district);

-- 频道：首页已改为「功能区」布局，频道只在「校园频道」页做二级分类
-- 「学习交流」(2002) 已下线：首页不再用频道 tab，该频道由「我想说」功能区替代
INSERT INTO t_channel (id, name, icon, description, school_id, sort) VALUES
(2001, '校园公告', '📢', '失物招领、校园通知、社团招新', NULL, 100),
(2003, '吃喝玩乐', '🍜', '食堂测评、周边探店、周末去哪儿', NULL, 80),
(2004, '树洞吐槽', '🕳️', '今天也想碎碎念一下', NULL, 70),
(2005, '二手交易', '♻️', '闲置好物流转，低碳又省钱', NULL, 60),
(2006, '运动搭子', '🏀', '篮球、羽毛球、夜跑约起来', NULL, 50)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 演示用户（已通过身份认证）platform='wx' 对应微信小程序
-- openid 用 config.js 的 DEV_OPENID，开发者工具免真机登录即可落到这个演示账号，
-- 从而能看到下面这份种子数据（商品 / 表白 / 帖子 / 私信会话）
INSERT INTO t_user (id, platform, openid, nickname, avatar, gender, school_id, student_no, real_name, verify_status, bio)
VALUES (9001, 'wx', 'dev_openid_guizhou_campus_001', '林小满', '', 2, 1005, '2022010101', '林满', 2, '浙大在读，喜欢逛食堂和拍照')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), school_id = VALUES(school_id), verify_status = VALUES(verify_status);

INSERT INTO t_goods (id, user_id, school_id, seller_name, title, description, price, original_price, category_id, condition_level, purchase_time, usage_frequency, source, warranty, support_trial, trade_type, location, status, view_count)
VALUES
(8001, 9001, 1005, '林小满', '九成新机械键盘 87 键', '去年双十一买的，青轴，手感很好。因为换静音轴了所以出，配件齐全，可当面验货。', 129.00, 299.00, 1, 2, '2023年11月', '每天使用', '自购自用', '官方保修已过', 1, 1, '紫金港校区 · 二食堂门口', 1, 56),
(8002, 9001, 1005, '林小满', '考研数学全套资料', '张宇基础30讲 + 1000题 + 真题，笔记很全，几乎没折角。适合刚开始准备的同学。', 45.00, 180.00, 2, 3, '2024年3月', '经常使用', '自购自用', '官方保修已过', 1, 1, '紫金港校区 · 图书馆', 1, 32)
ON DUPLICATE KEY UPDATE title = VALUES(title), condition_level = VALUES(condition_level), purchase_time = VALUES(purchase_time), usage_frequency = VALUES(usage_frequency), source = VALUES(source), warranty = VALUES(warranty), support_trial = VALUES(support_trial);

INSERT INTO t_wall_post (id, user_id, school_id, anon_name, anon_avatar, content, category, like_count)
VALUES
(7001, 9001, 1005, '匿名同学·037', 3, '图书馆四楼靠窗那个总穿灰色卫衣的男生，你昨天帮我把掉在地上的书捡起来了，一直没来得及说谢谢。', 'love', 24)
ON DUPLICATE KEY UPDATE content = VALUES(content);

INSERT INTO t_post (id, channel_id, user_id, school_id, author_name, title, content, like_count)
VALUES
(6001, 2003, 9001, 1005, '林小满', '紫金港食堂测评：这 5 个窗口闭眼入', '排名分先后：1. 三食堂麻辣香锅 2. 二食堂现烤面包 3. 一食堂手工水饺……', 18)
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- 市域演示数据：北京双校（本校 vs 本市切换的直观对比）
-- 北大同学的「本校」只看 1002 的帖子；切到「北京 · 全市」能看到 1001+1002 的全部帖子
INSERT INTO t_user (id, platform, openid, nickname, avatar, gender, school_id, student_no, real_name, verify_status, bio)
VALUES
(9005, 'wx', 'demo_openid_005', '园子日记', '', 1, 1001, '2021010001', '张园', 2, '清华园的日常记录'),
(9006, 'wx', 'demo_openid_006', '未名风', '', 2, 1002, '2021010002', '李名', 2, '未名湖畔在读')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

INSERT INTO t_post (id, channel_id, user_id, school_id, author_name, title, content, like_count)
VALUES
(6002, 2003, 9005, 1001, '园子日记', '清华观潮：听涛园新品测评', '听涛园新出的麻辣香锅值得一试，人均 25 吃到撑，晚上 8 点后还有折扣。', 12),
(6003, 2002, 9005, 1001, '园子日记', '学堂路自习室占座攻略', '期中周座位紧张，推荐三教晚上 9 点后捡漏，或者去李文正馆地下一层。', 9),
(6004, 2002, 9006, 1002, '未名风', '未名湖晨跑搭子招募', '每天早上 6:30 未名湖跑三圈，已坚持两周，欢迎新同学加入。', 15)
ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content);

INSERT INTO t_wall_post (id, user_id, school_id, anon_name, anon_avatar, content, category, like_count)
VALUES
(7002, 9005, 1001, '匿名同学·052', 5, '四教四楼那个总穿灰色卫衣的男生，你昨天帮我把掉在地上的书捡起来了，一直没来得及说谢谢。', 'love', 18),
(7003, 9006, 1002, '路过的同学·021', 2, '今天在二教楼下喂猫的时候遇到一个戴耳机的女生，她的猫缘也太好了吧。', 'findp', 7)
ON DUPLICATE KEY UPDATE content = VALUES(content);

-- 演示用的聊天对象（配合私信种子数据）
INSERT INTO t_user (id, platform, openid, nickname, avatar, gender, school_id, student_no, real_name, verify_status, bio)
VALUES
(9002, 'wx', 'demo_openid_002', '陈屿', '', 1, 1005, '2020010203', '陈屿', 2, '计算机学院，出闲置回消息很快'),
(9003, 'wx', 'demo_openid_003', '苏念', '', 2, 1005, '2023010512', '苏念', 1, '经管学院'),
(9004, 'wx', 'demo_openid_004', '麦麦学长', '', 1, 1005, '2019010708', '麦麦', 2, '热心课代表，资料整理狂魔')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

-- 私信会话：user_a 固定为较小 id；goods_id = 0 表示非商品会话
INSERT INTO t_message_conversation
(id, user_a, user_b, initiator_id, goods_id, goods_title, goods_price, goods_emoji, goods_status,
 anon, anon_name, last_msg_id, last_content, last_time, unread_a, unread_b, status)
VALUES
(3001, 9001, 9002, 9001, 8001, '九成新机械键盘 87 键', 129.00, '⌨️', 1,
 0, NULL, 4004, '可以的呀！今晚 8 点半后我都在寝室，带电脑当场试打，满意再付～',
 DATE_SUB(NOW(), INTERVAL 80 MINUTE), 2, 0, 1),
(3002, 9001, 9003, 9003, 0, NULL, NULL, NULL, NULL,
 1, '匿名纸条 #302号', 4013, '他说是计算机学院的，周三下午常在六教，你可以留意一下～',
 DATE_SUB(NOW(), INTERVAL 25 HOUR), 1, 0, 1),
(3003, 9001, 9004, 9001, 0, NULL, NULL, NULL, NULL,
 0, NULL, 4022, '宏观经济学期末真题汇总已经发到你校内邮箱啦，祝高分通过！✨',
 DATE_SUB(NOW(), INTERVAL 49 HOUR), 0, 0, 1),
(3004, 9001, 9003, 9003, 8002, '考研数学全套资料', 45.00, '📚', 1,
 0, NULL, 4032, '方便的～扫码吧，我带着资料在二号楼楼下等你',
 DATE_SUB(NOW(), INTERVAL 71 HOUR), 0, 0, 1)
ON DUPLICATE KEY UPDATE last_content = VALUES(last_content);

-- 私信消息（read_flag = 0 表示接收方未读）
INSERT INTO t_message (id, conversation_id, sender_id, receiver_id, type, content, read_flag, created_at)
VALUES
(4001, 3001, 9002, 9001, 'text', '同学你好！键盘还在的，配件都在，成色很新～', 1, DATE_SUB(NOW(), INTERVAL 95 MINUTE)),
(4002, 3001, 9001, 9002, 'text', '学长好！请问轴体可以现场试打吗？今晚在紫荆四号楼楼下方便面交吗👀', 1, DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(4003, 3001, 9002, 9001, 'location', '紫荆学生公寓4号楼 · 1层大厅服务台旁', 0, DATE_SUB(NOW(), INTERVAL 85 MINUTE)),
(4004, 3001, 9002, 9001, 'text', '可以的呀！今晚 8 点半后我都在寝室，带电脑当场试打，满意再付～', 0, DATE_SUB(NOW(), INTERVAL 80 MINUTE)),

(4011, 3002, 9003, 9001, 'text', '同学你好！看到你在六教302自习室发的捞人贴，那个经管学长我认识，可以帮你递张纸条～', 1, DATE_SUB(NOW(), INTERVAL 27 HOUR)),
(4012, 3002, 9001, 9003, 'text', '太感谢啦！麻烦帮我问一下他下周有没有空一起自习😊', 1, DATE_SUB(NOW(), INTERVAL 26 HOUR)),
(4013, 3002, 9003, 9001, 'text', '他说是计算机学院的，周三下午常在六教，你可以留意一下～', 0, DATE_SUB(NOW(), INTERVAL 25 HOUR)),

(4021, 3003, 9001, 9004, 'text', '学长，宏观经济学的期末复习资料方便发我一份吗？🙏', 1, DATE_SUB(NOW(), INTERVAL 50 HOUR)),
(4022, 3003, 9004, 9001, 'text', '宏观经济学期末真题汇总已经发到你校内邮箱啦，祝高分通过！✨', 1, DATE_SUB(NOW(), INTERVAL 49 HOUR)),

(4031, 3004, 9003, 9001, 'text', '学姐今晚8点在紫荆二号楼自提方便吗？带现金还是扫码呢？', 1, DATE_SUB(NOW(), INTERVAL 72 HOUR)),
(4032, 3004, 9001, 9003, 'text', '方便的～扫码吧，我带着资料在二号楼楼下等你', 1, DATE_SUB(NOW(), INTERVAL 71 HOUR))
ON DUPLICATE KEY UPDATE content = VALUES(content);

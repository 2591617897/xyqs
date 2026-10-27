# 项目长期约定（2026-10-27；细节见 daily log 与 NOTES-admin.md）

## 环境 / 构建
- 库 guizhou_campus_fun/root/123456；MySQL3306 Redis6379 Nacos8848 网关8888（唯一入口，前缀 `/api`）
- 端口 auth8101 user8102 goods8103 wall8104 post8105 message8106 admin8107 **promote8108**
- 线上 `https://campus-api.trkcwl.com`（服务器多项目→子域名带项目名）；`ACTIVE_ENV='prod'`；Nginx 模板 `deploy/nginx-campus-api.trkcwl.com.conf`
- 上域名必须 `UPLOAD_BASE_URL=<域名>`；上传目录必须**绝对路径**（`UploadPaths` 统一写读）；生产 Nginx alias 直出 `/uploads/`；图片地址只在 `utils/request.js` 归一
- 上传 404：`curl -D -` 见 `200 + code:404` = 写 A 读 B（相对路径 / Nacos 多实例）；微信后台配 request/uploadFile/downloadFile 三域名
- JDK17 `JAVA_HOME='C:\Program Files\Java\jdk-17'`，仓库 `D:\apache-maven-3.6.3\MavenData`；先 `cd backend && mvn -DskipTests clean install`；子模块只能 `mvn spring-boot:run`（`java -jar` 无主清单）
- `clean` 失败＝进程占用 target，先停服务；WorkBuddy 内启动加 `env -u SERVER__PORT`；「找不到符号 XxxClient」＝common jar 过期 → `mvn -pl campus-common -DskipTests install`
- 改网关路由**必须重启网关**（404=无路由，401=已路由只差登录）；父 POM 必须留 `-parameters`
- ⚠️ IDE（redhat.java）污染 `target/classes` → `NoClassDefFoundError`，先 `rm -rf target` 再 mvn

## 通用约定
- `{code,msg,data}`，0 成功；401 未登录 / 403 未认证 / 404 不存在
- **ID 一律字符串**（雪花19位，`JacksonConfig` 全局 Long→String）；前端**禁止** `Number(id)`
- 前端请求不写 `/api` 前缀（`BASE_URL` 已含，例外 `config.js` 的 `UPLOAD_URL`）；`Promise.all` 每项各自 `.catch`
- ⚠️ `utils/request.js` 导出**对象** → 只能 `http.get/post/put/del(...)`，**不能** `http({...})`
- 作者名/头像不能只靠快照：`PostService#refreshAuthors`、`GoodsService#refreshSellers` 用 `UserSnapshotMapper` 覆盖，新内容表照办

## 登录 / 游客 / 认证
- 只用 `utils/util.js` 的 `mp.*`，禁止判 `wx`/`qq`；`POST /api/auth/login {code,platform,devOpenid}`
- `t_user` 唯一键 `uk_platform_openid(platform,openid)`；头像 `chooseAvatar` + 昵称 `type="nickname"`
- 游客可浏览是设计：公开读 = GET + `/api/goods|wall|posts|channels`（排除 mine/orders/favorite）+ 精确 profile；网关只**精确匹配**正则，禁止宽泛放行 `/api/user`
- 不在启动时登录；`ensureLogin()` 无 token 先 `confirmLogin()`，拒绝绝不建号；`ensureLogin(true)` 只给明确意图
- 建号默认昵称「校园用户XXXX」、schoolId=null、verifyStatus=0；**`schoolId=null` 让「本校」筛选退化**（不加过滤＝看全站）→ 有选学校引导
- 「绑定学校」= schoolId!=null；「学籍认证」= verifyStatus==2，文案别混
- **JWT 的 schoolId/vs 是登录快照** → 改库后必须重签（`ensureLogin(true)` 或 `/user/verify` 新 token + `markTokenFresh()`）；`request.js` 只对 `code===404 && url 以 /user/me 开头` 做 `cleanLogin()`
- 凭据在 `campus-auth/.../application-local.yml`（gitignore）；`js_code=test`：40029 有效 / 40125 secret 错 / 40013 appid 错；`jscode2session` 响应 `text/plain` → `getForObject(String.class)` 再 ObjectMapper
- 位置服务 `sig=MD5(path?params+SK)`；`GET /api/auth/geo/ip-city`；key 日配额小
- 学校字典是 **`/schools`**（`AuthController` 无类级 `@RequestMapping`）；`GET /user/follow/list` 返**裸数组**；`/schools` limit ≥200
- 选学校排序：IP 给「XX自治州」，`t_school.city` 存州府 → 必走 `utils/school.js#PREFECTURE_SEAT`；IP 失败=腾讯 key 配额 121，弹层降级按城市分组
- 微信学生认证（`verify_source=2`）只回 `bind_status/is_student`，**不含学校/姓名/学号** → 学校仍需自选，`verify_status=2 ≠ 有学号`；默认关闭（类目白名单不含「社交-社区/论坛」），开关 `scripts/wx-student-plugin.js on|off`

## 安全基线（改动前跑 `scripts/security-baseline-check.js`，要求失败 0 项）
- **身份只认 JWT**：`SecurityAutoConfig` 不信 `X-User-Id`；后台 `AdminAuthInterceptor` 要求 token 与 `X-Admin-Id` 一致
- **密钥零硬编码**：`SecretStore`（common）/`SecretResolver`（gateway 不能依赖 common）；env → `~/.campus-secrets/*.key` → 随机；yml 只写 `${XXX:}`
- **令牌**：`iss=campus-api`+`aud=campus-miniapp|campus-admin`+`typ=user|admin`
- **后台口令 BCrypt(cost12)**：`AdminPasswordUtil.hash/matches/checkStrength`；旧 MD5 登录成功即升级 → admin=`Campus@2026#Temp`、auditor=`Campus@2026#Audit`；重置跑 `backend/sql/reset-admin-password.sql`；验证**只走网关** `POST :8888/api/admin/auth/login`（直连 8107 是 404）
- **令牌吊销** `t_admin_user.token_version` ↔ claim `tv`；改密/重置/禁用/删除 → `revokeTokens`；`/auth/password` 返新令牌必须 `setToken()`
- **敏感字段加密**：`@TableField(typeHandler=EncryptedStringTypeHandler.class)` + **`@TableName(autoResultMap=true)`**（漏了=写入加密读出密文）；加密列不能等值/LIKE → `SearchHash.of(scope,value)` 盲索引
- **上传**：扩展名 + `FileMagic` 文件头双校验；**网关 CORS 白名单**（`CORS_ALLOWED_ORIGINS`，allowCredentials=false），`/actuator` 只放 health
- **C 端频控** `LoginRateLimiter`：IP 30/min（code2Session 前）+ openid 10/min，超限 429
- **备份** `scripts/backup-db.sh` 必须设 `CAMPUS_BACKUP_PASSPHRASE`；生产 DB 必须 `campus_app`+`MYSQL_SSL=true`
- 未私有化：学生证照片走公网 `verify_img`；UGC/私信正文明文是有意保留 → `docs/安全审计报告.md`

## 小程序结构
- tabBar 4 真实页 + 中间发布钮：`index/zone/messages/user`；**`wall/index`、`goods/list` 已移出 → 只能 navigateTo**
- 分包：`subpackages/me`（goods/mine、goods/sold、dynamic、favorite）、`subpackages/msg`（notice/*4、settings、group/*3）
- 功能区 = `pages/zone/index`，入口只来自 `utils/zones.js#buildEntries`（3 固定 + t_channel 动态）；点「闲置交换」要清 `app.globalData.goodsMode`
- 首页 = 功能区布局（无频道 tab、无金刚区）：顶栏 → 公告位 → Banner → 今日最热/树洞闲谈/闲置交换/校园公告/校园生活
- 内容区一律**单列全宽**（`.zone-list` 纵向 flex，`.zcard` 100%）；每区最多 3 张（`ZONE_LIMIT`/`take()`），其余走「更多 ›」
- 闲置卡左图右文整行：图 200rpx、右侧 `flex:1`、价格 `margin-top:auto`
- 公告区(2001) 与生活区按 channelId 拆同一次 `/posts`；**空区不渲染**；频道 2002 已下线
- custom-tab-bar 自带未读角标（`attached` + `pageLifetimes.show` 拉 `/messages/unread`）
- ⚠️ 自定义 tabBar 压不住弹层：页面 `getTabBar().setData({hidden:true})`，`onShow` 里 `hidden:false` 兜底
- 浏览范围 `utils/scope.js`：`school`(默认)/`city`；城市走 IP 归属地（**不用 `wx.getLocation`**）
- 品牌=`校聚一城`；树洞=匿名心事、闲置=信息展示**禁止线上交易**（`tradeType` 固定 1、无支付/下单/邮寄）

## 布局 / 图片
- `scroll-view` 在 flex 列里必须 `min-height:0`；父容器要定高 `height` 不能只给 `max-height`；弹层 `height:Xvh` 必须 `box-sizing:border-box`
- 底部弹层避键盘：`adjust-position="{{false}}"` + `bindkeyboardheightchange` → `bottom:{{keyboardHeight}}px`
- tab 页底部留 ~110rpx + 安全区，非 tab 页不留
- `<image>` 与 `previewImage` 两条通道：真机 `http://192.168.x.x` 的 `<image>` 静默失败（灰块、binderror 不触发）→ 先用「预览」；真机看图要 HTTPS
- 查图 `curl -D -`：`Content-Type: image/*` 才对，`application/json` = 后端没返图
- `.json` 必须显式 `"usingComponents": {}`（缺了整页白屏）；自绘顶栏 `navigationStyle:custom`，图标用 SVG data-URI
- **引用根目录组件一律写 `/components/xxx/xxx`（带前导 `/`）**：漏 `/` 会被当相对路径；`check-miniprogram.js#checkComponentPaths` 静态查
- **主包只放主包用得到的 js/wxss**：只被某一分包引用的工具必须放进该分包（`subpackages/msg/utils/{ws,subscribe}.js`），否则「代码质量」报主包冗余

## 计数 / 关注 / 私信
- 「我的动态」只认 `UserVO.dynamicCount`；关注/粉丝用 `t_user.fans_count`/`follow_count` 冗余，同事务 + `greatest(x+delta,0)`
- `t_follow` `uk_pair(follower_id,followee_id)`；`DuplicateKeyException` 的 catch **必须直接 return**；粉丝数 `select ... for update`
- 会话 `user_a=min`/`user_b=max`、`goods_id=0`=非商品；删会话**只对自己隐藏**（`conversation_hide`）；撤回软删 `t_message.deleted`，撤回最后一条要回滚会话摘要
- `swipe-cell`：横向占优才接管、`open` 受控、圆角投影放宿主 class、插槽按钮 `catchtap`；**聊天页不要快捷话术 chips**
- 跳转一律 `catchtap` 防冒泡；`Post` 无 viewCount，显示浏览数要 `wx:if` 兜住；同校/同市比较用 `String(a)===String(b)`，任一为空不显示

## 内容安全
- **任何 UGC 写入口落库前必须过 `SecurityGuard`**：`checkText(userId,text,SCENE_*,BIZ_*)`，带图再 `checkImages`；新入口必须加进 `scripts/content-security-check.js` 的 `ENTRIES`
- 微信调用只在 `campus-auth`；业务侧走 Feign `SecurityClient`；文本同步拦（risky→400 不落库），图片 `media_check_async` 先发后审，结论靠 `/api/wx/callback` 落 `t_media_check`
- 违规写 `t_content_audit`：`source=2` 系统、`action=REJECT/WARN`、`target_id=0` 未落库；回调必须 **JSON + 明文模式**
- **fail-open**：微信异常一律放行 + warn → 「审核没生效」先查 IP 白名单(40164) 与 `WX_SEC_CHECK`/`WX_APPSECRET`
- ⚠️ **msg_sec_check / media_check_async 对 chunked POST 直接回 HTTP 412（无 body）**：Spring 两种 RequestFactory 都会退化成 chunked → 必须用 JDK `HttpClient` + `BodyPublishers.ofString`；踩中后 412 被 fail-open 吞成「放行」，只有一行 warn
- 自检 `GET :8101/internal/security/selfcheck`；配置项只在启动时读 → 改完必须重启 auth

## 运营位
- 敏感词落点 **`campus-auth`**（不是 admin）：`LocalSensitiveGuard` 60s 轮询；查库失败保留旧缓存；`selectEnabled()` 必须 `ORDER BY CHAR_LENGTH(word) DESC`
- **`SecurityGuard.purify()` 返回 String，必须拿返回值落库**；三级：1 WARN / 2 按 `replace_to`（空则 `***`）/ 3 抛 400；留痕 `ContentAuditTrailWriter(source=2)`
- 广告/公告在 campus-post 用 **VO 直接接收**；四道过滤：上架+过审+时间窗+地域
- ⚠️ `PUBLIC_READ_PREFIX` **只放行 GET**，任何 POST 必须另加精确正则
- ⚠️ `t_admin_notice.target_type` 是**安全边界**：1=后台内部通知，绝不能推小程序，只放行 2/3
- 首页 `Promise.all` 里运营位请求**不进 contentOk 判定**；`mockAds()` 返回 `[]`

## 推广（激励视频换排序）
- `campus-promote` 8108，网关 `/api/promote/**` **全部要登录**
- 链路：`quota` → `issue` 签一次性凭证（不加分）→ 播广告 → `complete` 结算 → 失败走 `cancel` 释放
- 单次 100 分，`有效分 = 100 × 剩余时间/7天` 线性衰减；定时任务每 30 分钟回写 `promote_score`
- 防刷：nonce 唯一键 + `status=0` 条件更新 / 最短 15s / 凭证 10 分钟 / 单条5·账号20 日上限；**同步内容服务失败必须抛异常回滚**，绝不 fail-open
- 三张内容表加 `promote_score`+`promote_expire_at`；只有默认 latest 排序插推广
- promote 不直接写别人的表 → 各内容服务提供 `/internal/{goods|posts|wall/posts}/owner` 与 `/promote/apply`
- 前端：`utils/rewarded-ad.js`（单例+重绑回调+show前load+超时兜底）、`utils/promote.js`、`components/promote-sheet`
- `PROMOTE_AD_UNIT_ID` 正式必填；**`PROMOTE_MOCK_AD` 生产必须 false**；开发者工具拉不起激励视频（要真机+流量主）；自测 `scripts/promote-selftest.js`
- ⚠️ 时间窗口校验一律用 `Duration.toMillis()`，**不要 `getSeconds()`**
- 见到 `501/502` 先查 8108 起没起（网关路由有 ≠ 实例在）；`node scripts/make-dev-token.js <userId> [schoolId] [vs]` 造 token

## 校友群（campus-message 8106，WS 实时）
- 一校一群 `t_group(type=1,biz_key=schoolId)`，6 表；后台菜单 **9320 段**（9310 与「系统日志」撞车）
- **项目无 Redis**：ticket/频控/禁言缓存/广播全是进程内单例 → 单实例 OK，多实例必须换 Redis
- 发消息裁定顺序 **单人禁言 → 夜间静音 → 频率 → 内容安全**，顺序错会给出误导性拒绝原因
- `clientMsgId` 走 `uk_client` 幂等（`DuplicateKeyException` 返已存在那条）；@所有人只更 `last_at_all_msg_id`
- auto-join 只认「无成员记录」；status=0/2 绝不自动拉回；建群 `DuplicateKeyException` **直接 return 重查结果**
- 绑定学校三入口统一走 `UserService#afterSchoolBound`，必须在 `updateById` **之后**（未提交跨服务查不到）
- **Nginx 必须独立 `location /api/ws`** 且写在 `location /` 之前（`Connection ""` 会覆盖 Upgrade 头）；网关 `lb:ws://` 路由 + `AuthGlobalFilter` 放行 WS_PREFIX（安全边界是一次性 ticket）
- 归档每天迁 12 个月前，批 500 + sleep 200ms，同步迁 `t_group_mention`
- 前端 `subpackages/msg/utils/ws.js`：退避 1→2→4→8s 封顶 15s、3 次失败降级轮询、重连**必须按 sinceId 补拉**；`onUnload` 先 `unbindWs()` 再断连
- 消息表有 `reply_to_id/name/text`（快照，不 JOIN）+ `deleted_by`(1 本人撤回 2 管理员)；撤回 `POST /groups/{id}/messages/{mid}/recall` 走 `toMillis` 判 2 分钟，广播 `recall` 帧（≠ 管理员 `delete`）
- 长按气泡 = 回复/撤回/复制/举报（`GROUP_MSG`）；长按头像 = @；行 id `m-{{id}}`、滚动锚点 `m-last`；输入栏必须包在 `.bottom-dock` 里才能随键盘抬

## 后台
> 细节见同目录 **`NOTES-admin.md`**（网关分层、独立 JWT、AdminWriteGateway 降级、el-container 竖排、el-pagination total 必须 Number、动态路由幂等、`t_notification.content_snapshot`），做后台任务先读它。

## 演示数据 / 自测
- `config.js` 的 `DEV_OPENID` 对应演示账号（2114018677295706113 莫东海 / school 1001 / vs=2），别破坏
- node 用 `C:/Users/25916/.workbuddy/binaries/node/versions/22.22.2-6/node.exe`
- `check-miniprogram.js` 结构 23 主包 + 12 分包 / `mock-selftest` 155 / `home-zones` 35 / `content-security` 35 / `message-notify` 64 / `api-smoke-test` 45（**跑完清演示数据**）
- 改首页卡片条数时三套里 `items.length===6` 的断言必须同步改

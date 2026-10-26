# 项目长期约定（2026-10-26 精简，细节见同日 daily log）

## 环境
- 库 guizhou_campus_fun/root/123456；MySQL3306 Redis6379 Nacos8848 网关8888（唯一入口，前缀 `/api`）
- 端口 auth8101 user8102 goods8103 wall8104 post8105 message8106 admin8107
- 线上 `https://campus-api.trkcwl.com`（服务器多项目→子域名带项目名）；`ACTIVE_ENV='prod'`；Nginx 模板 `deploy/nginx-campus-api.trkcwl.com.conf`
- 上域名必须 `UPLOAD_BASE_URL=<域名>`；上传目录必须**绝对路径**（`UploadPaths` 统一写读）；生产 Nginx alias 直出 `/uploads/`（`/mnt/ssd/web/campus-api.trkcwl.com/uploads/`）
- 图片地址只在 `utils/request.js` 归一（递归替换含 `/uploads/` 的串）
- 上传 404：`curl -D -` 见 `200 + code:404` = 写 A 读 B（相对路径 / Nacos 多实例）
- 微信后台分别配 request/uploadFile/downloadFile 三域名（无端口路径 IP，需备案）

## 构建
- JDK17 `JAVA_HOME='C:\Program Files\Java\jdk-17'`，仓库 `D:\apache-maven-3.6.3\MavenData`
- 先 `cd backend && mvn -DskipTests clean install`；子模块只能 `mvn spring-boot:run`（`java -jar` 无主清单）
- `clean` 失败＝进程占用 target，先停服务；WorkBuddy 内启动加 `env -u SERVER__PORT`
- 「找不到符号 XxxClient」＝common jar 过期 → `mvn -pl campus-common -DskipTests install`
- 改网关路由**必须重启网关**（404=无路由，401=已路由只差登录）；父 POM 必须留 `-parameters`

## 通用约定
- `{code,msg,data}`，0 成功；401 未登录 / 403 未认证 / 404 不存在
- **ID 一律字符串**（雪花19位，`JacksonConfig` 全局 Long→String），前端别 `Number(id)`
- 前端请求不写 `/api` 前缀（`BASE_URL` 已含，例外 `config.js` 的 `UPLOAD_URL`）；`Promise.all` 每项各自 `.catch`
- 作者名/头像不能只靠快照：`PostService#refreshAuthors`、`GoodsService#refreshSellers` 用 `UserSnapshotMapper` 覆盖，新内容表照办
- 静态资源映射必须 `Path#toUri()`

## 登录 / 游客
- 只用 `utils/util.js` 的 `mp.*`，禁止判 `wx`/`qq`；`POST /api/auth/login {code,platform,devOpenid}`
- `t_user` 唯一键 `uk_platform_openid(platform,openid)`；头像 `chooseAvatar` + 昵称 `type="nickname"`
- 游客可浏览是设计：公开读 = GET + `/api/goods|wall|posts|channels`（排除 mine/orders/favorite）+ 精确 profile
- 不在启动时登录；`ensureLogin()` 无 token 先 `confirmLogin()`，拒绝绝不建号；`ensureLogin(true)` 只给明确意图
- 建号默认昵称「校园用户XXXX」、schoolId=null、verifyStatus=0
- **`schoolId=null` 让「本校」筛选退化**（不加过滤＝看全站）→ 所以有选学校引导
- 「绑定学校」= schoolId!=null；「学籍认证」= verifyStatus==2，文案别混
- **JWT 的 schoolId/vs 是登录快照** → 改库后必须重签（`ensureLogin(true)` 或 `/user/verify` 新 token + `markTokenFresh()`）
- `request.js` 只对 `code===404 && url 以 /user/me 开头` 做 `cleanLogin()`
- 凭据在 `campus-auth/.../application-local.yml`（gitignore）；`js_code=test`：40029 有效 / 40125 secret 错 / 40013 appid 错
- `jscode2session` 响应 `text/plain` → `getForObject(String.class)` 再 ObjectMapper
- 位置服务 `sig=MD5(path?params+SK)`；`GET /api/auth/geo/ip-city`；key 日配额小，上线要申请

## 安全基线（2026-10-26 全量加固后，改代码前先读）
- **身份只认 JWT**：`SecurityAutoConfig` 不再信任 `X-User-Id`（带了但无有效 token 直接 401）；
  后台 `AdminAuthInterceptor` 要求 token 与 `X-Admin-Id` 一致、权限读 claim。别再把「网关注入头」当可信来源
- **密钥零硬编码**：`SecretStore`（common）/`SecretResolver`（gateway，不能依赖 common 否则 WebFlux 冲突）：
  env → `~/.campus-secrets/<jwt.key|admin-jwt.key|data-key.key>` → 随机生成。yml 里只写 `${XXX:}`
- **令牌**：`iss=campus-api` + `aud=campus-miniapp|campus-admin` + `typ=user|admin`；改密钥后存量 token 全失效
- **后台口令 BCrypt(cost12)**：`AdminPasswordUtil.hash/matches/checkStrength`；旧 MD5 登录成功即自动升级，
  别再写 `md5Hex(plain, salt)`；新增/改密必须过 `checkStrength`
- **敏感字段加密**：`@TableField(typeHandler = EncryptedStringTypeHandler.class)` +
  **`@TableName(autoResultMap = true)`（漏了就会写入加密、读出密文）**
  加密列不能等值/LIKE → 用 `SearchHash.of(scope, value)` 盲索引（学号 `student_no_hash`、手机号 `phone_hash`）
  历史数据迁移：`CAMPUS_SECURITY_MIGRATE_PLAINTEXT=true` 启动一次后必须关掉
- **上传**：扩展名 + `FileMagic` 文件头双校验，新入口照办
- **网关**：CORS 是白名单（`CORS_ALLOWED_ORIGINS`）且 `allowCredentials=false`；`/actuator` 只放行 health
- **新增 UGC/个人信息落库前**：先判断是否该加密（手机号/学号/姓名/联系方式/证件），再判断是否需要盲索引
- 数据库：生产必须 `campus_app`（仅 DML）+ `MYSQL_SSL=true`；脚本在 `sql/migration-20261026-security-hardening.sql`
- ⚠️ IDE（redhat.java）会污染 `target/classes` 导致 `NoClassDefFoundError: LikeMapper` → 先 `rm -rf target` 再 mvn

## 小程序结构
- tabBar 4 真实页 + 中间发布钮：`index/zone/messages/user`；**`wall/index`、`goods/list` 已移出 → 只能 navigateTo**
- 功能区 = `pages/zone/index`，入口只来自 `utils/zones.js#buildEntries`（3 固定 + t_channel 动态）
- 点「校园闲置」要清 `app.globalData.goodsMode`，否则落在「我的闲置/收藏」筛选态
- 首页 = 功能区布局（**无频道 tab、无金刚区**）：顶栏 → 公告位 → 今日最热/我想说/校园闲置/校园公告/校园生活
- 内容区一律**单列全宽**（`.zone-list` 纵向 flex，`.zcard` 100%）；别写横向 scroll-view + 固定宽卡
- 每区最多 3 张（`ZONE_LIMIT`/`take()`），其余走「更多 ›」；改布局同步 `zones.js` 与两套自测断言
- 闲置卡左图右文整行：图 200rpx、右侧 `flex:1`、价格 `margin-top:auto`
- 公告区(2001) 与生活区按 channelId 拆同一次 `/posts`；**空区不渲染**；频道 2002 已下线
- 点赞按 kind 分流；表白墙派生字段在 `utils/wall.js`
- custom-tab-bar 自带未读角标（`attached` + `pageLifetimes.show` 拉 `/messages/unread`，无 token 归 0）
- ⚠️ 自定义 tabBar 压不住弹层：页面 `getTabBar().setData({hidden:true})`，`onShow` 里 `hidden:false` 兜底；`getTabBar()` 只有 tab 页有

## 布局 / 图片
- `scroll-view` 在 flex 列里必须 `min-height:0`；**父容器要定高 `height` 不能只给 `max-height`**；弹层 `height:Xvh` 必须 `box-sizing:border-box`
- 底部弹层避键盘：`adjust-position="{{false}}"` + `bindkeyboardheightchange` → `bottom:{{keyboardHeight}}px`
- tab 页底部留 ~110rpx + 安全区，非 tab 页不留
- `<image>` 与 `previewImage` 两条通道：真机 `http://192.168.x.x` 的 `<image>` 静默失败（灰块、binderror 不触发）→ 先用「预览」；真机看图要 HTTPS
- 查图 `curl -D -`：`Content-Type: image/*` 才对，`application/json` = 后端没返图
- `.json` 必须显式 `"usingComponents": {}`（缺了整页白屏）；`require` 相对路径按深度数层级；自绘顶栏 `navigationStyle:custom`，图标用 SVG data-URI

## 接口坑
- 学校字典是 **`/schools`**（`AuthController` 无类级 `@RequestMapping`）
- `GET /user/follow/list` 返回**裸数组** → `Array.isArray(res) ? res : (res?.list||[])`
- 网关公开只能**精确匹配**（`^/api/user/\d+/profile$` 等），禁止宽泛放行 `/api/user`
- 跳转一律 `catchtap` 防冒泡；`Post` 无 viewCount 字段，显示浏览数要 `wx:if` 兜住
- 同校/同市：`String(myUser.schoolId)===String(item.schoolId) ? '同校' : '同市'`，任一为空不显示

## 计数 / 关注 / 私信
- 「我的动态」只认后端 `UserVO.dynamicCount`；关注/粉丝用 `t_user.fans_count`/`follow_count` 冗余，同事务 + `greatest(x+delta,0)`
- `t_follow` `uk_pair(follower_id,followee_id)`；`DuplicateKeyException` 的 catch **必须直接 return**（否则 +2 漂移）；粉丝数 `select ... for update`
- 会话 `user_a=min`/`user_b=max`、`goods_id=0`=非商品；删会话**只对自己隐藏**（`conversation_hide`，对方发新消息自动取消）；撤回软删 `t_message.deleted`，撤回最后一条要回滚会话摘要
- `swipe-cell`：横向占优才接管、`open` 受控、圆角投影放宿主 class、插槽按钮直接 `catchtap`；**聊天页不要快捷话术 chips**（用户明确要求）

## 内容安全（长期）
- **任何 UGC 写入口落库前必须过 `SecurityGuard`**：`checkText(userId,text,SCENE_*,BIZ_*)`，带图再 `checkImages`；新增入口必须加进 `scripts/content-security-check.js` 的 `ENTRIES`
- 微信调用只允许在 `campus-auth`；业务侧走 Feign `SecurityClient`；文本同步拦（risky→400 不落库），图片 `media_check_async` 先发后审，结论靠 `/api/wx/callback` 落 `t_media_check`
- 违规写 `t_content_audit`：`source=2` 系统、`action=REJECT/WARN`、`target_id=0` 未落库
- **fail-open**：微信异常一律放行 + warn → 「审核没生效」先查 IP 白名单(40164) 与 `WX_SEC_CHECK`/`WX_APPSECRET`
- 回调必须 **JSON + 明文模式**，Token 同 `WX_CALLBACK_TOKEN`
- ⚠️ **微信 msg_sec_check / media_check_async 对 chunked 的 POST 直接回 HTTP 412（无 body）**：
  Spring 的 SimpleClientHttpRequestFactory 和 JdkClientHttpRequestFactory **都会退化成 chunked**
  → 这两个接口必须用 JDK `HttpClient` + `BodyPublishers.ofString`（定长）。踩中后 412 被 fail-open
  吞成「放行」，症状是「配好了却什么都拦不住」，只有一行 warn
- 配置自检 `GET :8101/internal/security/selfcheck`（真拉 token + 真发一次 msg_sec_check，
  返回 token/probe/hints/ready，不写流水）；配置项只在启动时读 → 改完必须重启 auth（8101）

## 运营位（2026-10-25 打通）
- 敏感词落点 **`campus-auth`**（不是 admin）：`LocalSensitiveGuard` 60s 轮询刷新，不依赖 admin 推送；查库失败保留旧缓存
- `selectEnabled()` 必须 `ORDER BY CHAR_LENGTH(word) DESC`（长词优先）
- **`SecurityGuard.purify()` 返回 String，必须拿返回值落库**，否则 level2 替换不生效；三级：1 WARN 不改 / 2 按 `replace_to`（空则 `***`）/ 3 抛 400；留痕走 `ContentAuditTrailWriter(source=2)`
- 广告/公告 campus-post 侧用 **VO 直接接收**（不建实体副本）；四道过滤：上架+过审+时间窗+地域
- ⚠️ `PUBLIC_READ_PREFIX` **只放行 GET** —— 任何 POST（上报点击）必须另加正则精确放行
- ⚠️ `t_admin_notice.target_type` 是**安全边界**：1=后台内部通知，绝不能推给小程序，只放行 2/3
- 首页 `Promise.all` 里运营位请求**不进 contentOk 判定**（后台没配广告不能判整页失败）；`mockAds()` 返回 `[]`

## 后台（campus-admin 8107 + admin-web）
> 细节见同目录 **`NOTES-admin.md`**（网关分层、独立 JWT、AdminWriteGateway 降级、el-container 竖排、el-pagination total 必须 Number、动态路由幂等、t_notification.content_snapshot 等），做后台任务先读它。

## 安全基线（长期，改动前先跑 `scripts/security-baseline-check.js`，要求「失败 0 项」）
- **后台口令不是 admin123**：admin=`Campus@2026#Temp`、auditor=`Campus@2026#Audit`（`$2a$12$`、`must_change_pwd=1`）
- **令牌吊销**：`t_admin_user.token_version` ↔ claim `tv`，拦截器不缓存地比对；改密/重置/禁用/删除 → `revokeTokens`
  → **`/auth/password` 返回新令牌**，前端必须 `setToken()` 替换；`CAMPUS_ADMIN_FORCE_PWD_CHANGE=false` 才能跳过强制改密
- **C 端频控** `LoginRateLimiter`：IP 30/min（在 code2Session 之前）+ openid 10/min；超限 429
- **备份**：`scripts/backup-db.sh` 必须设 `CAMPUS_BACKUP_PASSPHRASE`，否则拒绝执行（不允许明文备份）
- 学生证照片仍走公网静态目录（`verify_img`），未私有化；UGC/私信正文明文是有意保留
- 完整结论与上线顺序：`docs/安全审计报告.md`（第八节是第二轮）

## 演示数据 / 自测
- `config.js` 的 `DEV_OPENID` 对应 t_user 9001（林小满），别破坏
- node 用 `C:/Users/25916/.workbuddy/binaries/node/versions/22.22.2-6/node.exe`
- `check-miniprogram.js` 结构 23 页 / `mock-selftest.js` 154 断言 / `home-zones-selftest.js` 35 断言 / `content-security-check.js` 35 断言 / `api-smoke-test.js` 后端 45 项（**跑完清演示数据**）
- 改首页卡片条数时三套里 `items.length===6` 的断言必须同步改

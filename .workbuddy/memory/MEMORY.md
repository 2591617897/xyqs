# 项目长期约定（精简版 · 2026-10-25 二次整理）

## 平台 / 环境
- 业务只用 `utils/util.js` 导出的 `mp.*`，禁止直接判断 `wx`/`qq`（`PLATFORM` 在 util 内）
- 登录带 platform：`POST /api/auth/login {code, platform, devOpenid}`；配置 `campus.wx/qq.appid|appsecret`、`campus.auth.mock`
- `t_user` 唯一键 **`uk_platform_openid(platform, openid)`**（openid 只在平台内唯一），别改回单列
- 头像 `open-type="chooseAvatar"`（临时路径，先上传再入库）+ 昵称 `input type="nickname"`（getUserInfo/UserProfile 已废弃）
- 库 `guizhou_campus_fun`/root/123456；MySQL 3306、Redis 6379、Nacos 8848、网关 8888（唯一入口，前缀 `/api`）
- 端口：auth 8101 / user 8102 / goods 8103 / wall 8104 / post 8105 / message 8106
- 线上域名 `https://campus-api.trkcwl.com`（该服务器跑多项目 → 子域名必带项目名）；`ACTIVE_ENV` 切 `'prod'`；Nginx 模板 `deploy/nginx-campus-api.trkcwl.com.conf`（443→8888，upstream `campus_gateway`）
- 上域名后必须设 `UPLOAD_BASE_URL=<域名>`，否则 `UploadController` 按内网默认拼地址
- **上传目录必须绝对路径**：`./uploads` 相对进程 cwd（systemd/IDE/nohup 三种 cwd 不同），已用 `UploadPaths` 统一写读；生产由 Nginx alias 直出 `/uploads/`（服务器路径 `/mnt/ssd/web/campus-api.trkcwl.com/uploads/`）
- 图片地址**只在 `utils/request.js` 归一**（递归替换含 `/uploads/` 的字符串），页面不要再各自处理
- 「上传成功但 404」先看 `curl -D -`：`200 + {"code":404}` = 写 A 读 B（相对路径 or Nacos 多个 campus-user 实例）；看启动日志 `[upload] 静态资源映射`
- 微信后台须分别配 `request`/`uploadFile`/`downloadFile` 三个域名（无端口/路径/IP，需 ICP 备案）
- 改端口/库名要同步：`docker-compose.yml`、`sql/schema.sql`、`sql/data.sql`、5 个 `application.yml`、`utils/config.js`、README、docs

## 构建 / 启动
- Maven 需 **JDK 17**：`JAVA_HOME='C:\Program Files\Java\jdk-17'`；本地仓库 `D:\apache-maven-3.6.3\MavenData`
- 先 `cd backend && mvn -DskipTests clean install`（子模块否则取不到 `campus-common`）
- 子模块是普通 jar → 只能 `mvn spring-boot:run`；`java -jar` 报「没有主清单属性」
- `clean` 失败＝进程占用 target，先停服务再编译（否则坏 class → `NoClassDefFoundError: R`）
- WorkBuddy 内启动加 `env -u SERVER__PORT`
- 「找不到符号 XxxClient」＝common jar 过期 → `mvn -pl campus-common -DskipTests install`
- 改网关路由**必须重启网关**（404=无路由，401=已路由只差登录）
- 父 POM 必须保留 `-parameters`（未继承 spring-boot-starter-parent），否则 `@RequestParam` 全 400

## 后端约定
- 统一响应 `{code,msg,data}`，0 成功；401 未登录 / 403 未认证 / 404 不存在
- **ID 一律字符串传输**（雪花 19 位超 JS 安全整数），`JacksonConfig` 全局 Long→String；前端别 `Number(id)`
- 前端请求**不要再写 `/api` 前缀**（`BASE_URL` 已含；唯一例外 `config.js` 的 `UPLOAD_URL`）
- `Promise.all` 每个请求各自 `.catch`，否则一个失败吞整页
- 作者名/头像不能只靠快照：`PostService#refreshAuthors`、`GoodsService#refreshSellers` 用 `UserSnapshotMapper` 覆盖；新内容表照办
- 静态资源映射必须 `Path#toUri()`（`"file:"+"C:/x"` 静默失效 → 500 → 灰块）
- `GlobalExceptionHandler` 已把 `NoResourceFoundException` 单独返回 404
- 内部计数（直连）：`GET :8105/internal/posts/count`、`:8104/internal/wall/posts/count?category=`

## 底部 tabBar（2026-10-25 调整后）
- 结构：**首页 / 功能区 / ＋（发布面板） / 私信 / 我的**，共 4 个真实 tab 页 + 中间凸起发布钮
- `app.json` 的 `tabBar.list` 现为：`pages/index/index`、`pages/zone/index`、`pages/messages/index`、`pages/user/index`
  （**`pages/wall/index`、`pages/goods/list` 都已移出 tabBar** → 只能 navigateTo，禁止 switchTab）
- 「功能区」= `pages/zone/index`，内容就是原首页金刚区：入口只来自 `utils/zones.js#buildEntries`（3 固定 + t_channel 动态），**首页不再渲染入口**
- 点击「校园闲置」入口要清 `app.globalData.goodsMode`，否则会落在「我的闲置/收藏」筛选态
- custom-tab-bar 自带 `unread` 角标：`refreshUnread()` 在 `attached` + `pageLifetimes.show` 拉 `GET /messages/unread`（无 token 直接归 0，不触发登录）；消息页 `load()` 后用 `summary.unreadTotal` 回写一次
- ⚠️ 自定义 tabBar 压不住弹层：页面 z-index 再高也盖不过，必须由页面 `getTabBar().setData({hidden:true})` 隐藏、关闭恢复，并在 `onShow` 里 `hidden:false` 兜底（首页/我的选学校弹层、消息页通知抽屉已补）
- 页面的 `getTabBar()` 只有 tab 页（index/zone/messages/user）才有；非 tab 页调它拿到 undefined，所以旧代码里 `setData({selected:N})` 要随 tab 归属一起清理

## 首页 = 功能区布局（**没有频道 tab，也不再有金刚区**）
- 结构：顶栏（范围胶囊/搜索/铃铛）→ 运营公告位 → 内容功能区（今日最热 / 我想说 / 校园闲置 / 校园公告 / 校园生活）
- **内容区一律「单列全宽」卡片**：`.zone-list` 纵向 flex，`.zcard` 为 `display:block; width:100%`；
  **不要再写成横向 `scroll-view` + 固定宽度卡片**（以前 400/340/240rpx 看着像分了两栏，2026-10-25 改掉）
- 单列后每区最多 3 张卡，由 `utils/zones.js` 的 **`ZONE_LIMIT`/`take()`** 收敛，其余走右上角「更多 ›」；
  改卡片布局时记得同步 `zones.js` 的 limit 与两套自测断言（`==6` 已改 `<=3`）
- 校园闲置卡是「左图右文」整行：图 200rpx 固定、右侧 `flex:1`，价格 `margin-top:auto` 贴底
- **唯一配置源 = `miniprogram/utils/zones.js`**（buildEntries / buildZones / postItem / wallItem / goodsItem）；
  `buildEntries` 现在只给 `pages/zone/index` 用，加功能＝加一条 zone 配置 + 选模板，不动 tabBar（名额是微信硬上限 5）
- 公告区(channel 2001) 与生活区在前端按 channelId 拆同一次 `/posts` 响应；**空区不渲染**
- 点赞按 kind 分流：post `/posts/{id}/like`、wall `/wall/posts/{id}/like`、goods `/goods/{id}/like`
- 表白墙派生字段抽到 `utils/wall.js`（decorateWall / CAT_TAG），首页与表白墙页共用
- 频道 2002「学习交流」已下线（迁移 `backend/sql/migration-20261025-home-zones.sql`）

## 登录 / 游客
- 游客可浏览是设计；公开读 = GET + `/api/goods|wall|posts|channels`（排除 mine/orders/favorite）+ 精确 profile
- 不在启动时登录；`ensureLogin()` 无 token 时先 `confirmLogin()` 征得同意，拒绝绝不建号；`ensureLogin(true)` 仅用于明确意图（点「一键登录」/404 自愈）
- 建号默认：昵称「校园用户XXXX」、schoolId=null、verifyStatus=0
- **`schoolId=null` 会让「本校」筛选退化**（`.eq(cond,...)` 不加过滤 → 看全站），所以有选学校引导
- 「绑定学校」= schoolId != null；「学籍认证」= verifyStatus == 2，文案别混；`PUT /user/me` 的 schoolId 只给未认证用户
- **JWT 里 schoolId/vs 是登录快照** → 改库后必须重签（`ensureLogin(true)` 或 `/user/verify` 返回的新 token + `markTokenFresh()`）
- `request.js` 只对 `code===404 && url 以 /user/me 开头` 做 `cleanLogin()`（锁死条件，防止误踢浏览者）
- 凭据在 `campus-auth/src/main/resources/application-local.yml`（gitignore）；自检 `js_code=test` → 40029 有效 / 40125 secret 错 / 40013 appid 错
- `jscode2session` 响应头 `text/plain` → 用 `getForObject(url, String.class)` 再 ObjectMapper
- 腾讯位置服务开签名校验需 `sig=MD5(path?params+SK)`；IP 定位 `GET /api/auth/geo/ip-city`（不选学校时推荐同城），**key 每日配额很小，上线要申请**

## 计数口径
- 「我的动态」＝ 帖子 + 表白墙全部类别，只认后端 `UserVO.dynamicCount`（前端相加会漏 find/findp）
- 关注/粉丝数用 `t_user.fans_count`/`follow_count` 冗余，关系表操作与两条 UPDATE 同事务，`greatest(x+delta,0)` 防负

## 关注 / 粉丝 / 互关
- `t_follow`：`uk_pair(follower_id, followee_id)`；迁移 `migration-20261025-follow-profile.sql`
- 接口：`POST|DELETE /api/user/follow/{id}`、`/api/user/follow/status?userId=`、`/api/user/follow/list?type=following|fans`、`GET /api/user/{id}/profile`（公开）
- `FollowUserVO`/`ProfileVO` 返回 `followedBy`；前端 `mutual = isFollowing && followedBy` → 「相互关注」
- `DuplicateKeyException` 的 catch 必须直接 return（否则计数双插 +2 漂移）；粉丝数用 `select ... for update`

## 私信（campus-message 8106）
- 会话 `t_message_conversation`（`user_a=min(id)`/`user_b=max(id)`，`goods_id=0`=非商品）、消息 `t_message`
- 删会话＝**只对自己隐藏**（`t_message_conversation_hide`，uk 幂等，列表/未读用 not exists 排除）；对方发新消息或重新 start 自动取消隐藏
- 撤回＝软删 `t_message.deleted`，仅本人；**撤回最后一条要回滚会话摘要**
- 列表 `lastFrom`(me/peer) 走 `selectSenders` 批量查，失败 try/catch 降级
- 左滑组件 `components/swipe-cell`：横向占优才接管手势、`open` 受控（页面 `openSwipeId`）、圆角/投影放宿主 class、插槽内 id 用 `swipe-id` 透传、插槽按钮直接 `catchtap` 父方法
- 聊天页只有「我发的」可左滑撤回；**不要加快捷话术 chips**（用户明确要求）
- 消息页已是 tab 页：刷新放 `onShow`，顶栏去掉返回箭头，游客态显示登录引导（`needLogin` + `goLogin`）

## 布局铁律
- `scroll-view` 在 flex 列里必须 `min-height: 0`，否则不滚动
- **父容器要定高 `height`，不能只给 `max-height`**（内容驱动 → flex:1 拿到内容高度 → 不滚动不裁剪）。可用写法：弹层定高（verify 76vh / onboard 78vh）或 scroll-view 自带 `max-height`（`user/.order-scroll`）
- 弹层写死 `height:Xvh` 必须配 `box-sizing: border-box`
- 底部弹层输入框手动避键盘：`adjust-position="{{false}}"` + `bindkeyboardheightchange` → `bottom: {{keyboardHeight}}px`
- tab 页底部要给底栏留高度（约 110rpx + 安全区），非 tab 页不用留

## 图片
- `<image>` 与 `previewImage` 是两条下载通道：真机 `http://192.168.x.x` 的 `<image>` 静默失败（灰块、binderror 不触发），previewImage 正常 → 不是代码问题
- 「真机调试」模式 `<image>` 有已知问题，先用「预览」验证；局域网 IP 加不进 downloadFile 白名单 → 真机看图要 HTTPS
- 排查顺序：`curl -D - -o f <url>`，`Content-Type: image/png|jpeg` 才对；`application/json` = 后端没返回图片

## 同校/同市标签
- `String(myUser.schoolId) === String(item.schoolId) ? '同校' : '同市'`，任一为空不显示
- 已统一：首页信息流（仅全市模式打标）、帖子详情、表白墙详情（`sameSchool`）

## 页面文件规范
- 每页 4 件套；`.json` 必须显式写 `"usingComponents": {}`（缺了整页白屏）
- require 相对路径按深度数层级（`pages/user/space/space.js` = `../../../utils/`），写错只白屏不报错
- 自绘顶栏必须 `navigationStyle: custom`；图标用 SVG data-URI，别用 ←/✓/☰ 文本字符

## 接口坑（踩过，别再犯）
- **学校字典是 `/schools` 不是 `/auth/schools`**：`AuthController` 无类级 `@RequestMapping`，写错网关直接 404
- **`GET /user/follow/list` 返回裸数组**（不是 `{list:[]}`）→ 一律 `Array.isArray(res) ? res : ((res && res.list) || [])`
- 网关公开只能**精确匹配**（`^/api/user/\d+/profile$`、`/api/user/follow/status`），**禁止宽泛放行 `/api/user`**（会误开 /user/me）
- 跳转一律 `catchtap`（头像/昵称区），否则冒泡进详情
- 后端 `Post` 实体**没有 viewCount 字段**，显示浏览数要 `wx:if` 兜住

## 视觉 / UI 重构（2026-10-25 拍板，进行中）
- **自建 `ui-*` 轻量组件库，不引入 Vant / TDesign / WeUI**（无 package.json、无 CI，且已有 84 个公共 class / 渐变 Hero / CSS 画形头像 / 自研 swipe-cell 都无第三方对应物）
- **主色森林绿 `--brand-500 #006C49`**（白字对比度 6.48 ✅）；樱花粉降级为情感子色 `--like-500 #FF4D7D`（表白墙/点赞/收藏）
- token 单一来源 `styles/tokens.wxss`、图标 `styles/icons.wxss`，`app.wxss` 顶部 `@import`
- 图标 = SVG data-URI + `-webkit-mask-image` + `background-color:currentColor`；命名 `ico-{语义}[-fill|-o]`，**尺寸不进 class**，走 ui-icon 的 `size`
- 阴影只留 3 个 token `--shadow-1/2/3`（原 39 套），全部 `rgba(15,23,42,…)` 冷中性
- 圆角 `--radius-lg 24rpx`；弹层高度 `--sheet-max-h 78vh`；tabbar 预留 `--tabbar-height 120rpx`（原 170/180/190 三档）
- ⚠️ `page{}` 作用域的 CSS 变量对 `custom-tab-bar` **失效** → 必须在其 wxss 顶部冗余写一份 token 副本，否则底栏变黑
- ⚠️ 所有 `var()` **必须带 fallback**（`--mac-cream` 未定义无 fallback 已静默失效一次）
- `pages/user/index.wxss` 的 23 个 `--mac-*` 局部变量覆盖了 9 个全局同名变量 → 待删

## 演示数据 / 校验脚本
- `config.js` 的 `DEV_OPENID` 对应 t_user 9001（林小满），改种子数据别破坏这个对应
- 三套前端自测跑法一致：`node scripts/xxx.js`（node 用 `C:/Users/25916/.workbuddy/binaries/node/versions/22.22.2-6/node.exe`）
  - `check-miniprogram.js`：结构完整性，**22 页**
  - `mock-selftest.js`：模拟层，**154 断言**（含 zones 单列全宽「每区 ≤3 条」）
  - `home-zones-selftest.js`：首页功能区回归，**35 断言**（含 zone 页 onShow 与跳转落点）
  - `api-smoke-test.js`：后端 **45 项**，需 Nacos + 服务已启动，**跑完清演示数据**（删 `content='冒烟测试消息'` 并重置会话 last_*/unread_a）
- 改首页卡片布局/条数时，三套里凡断言 `items.length === 6` 的地方必须同步改，否则必红

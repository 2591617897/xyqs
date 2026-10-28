# 校园趣事（校聚一城）长期记忆索引

> 详细工程约定已归档到 **`NOTES-engineering.md`**（环境/端口/上传坑/小程序布局/可见范围/邮件/推广/需求广场等），
> 后台专项见 **`NOTES-admin.md`**，逐日细节见 `YYYY-MM-DD.md`。本文件只保留**必须条件反射**的红线。

## 环境
- 库 `guizhou_campus_fun`；Nacos 8848、网关 8888（唯一入口，前缀 `/api`）；线上 `campus-api.trkcwl.com`
- 端口：auth8101 user8102 goods8103 wall8104 post8105 message8106 admin8107 promote8108 mail8109
- 后端 `mvn -DskipTests clean install`；子模块只能 `spring-boot:run`；**改网关路由必须重启网关**

## 接口契约
- `{code,msg,data}`，0 成功；**ID 一律字符串**（Long→String），前端禁止 `Number(id)`
- 前端请求不写 `/api`；`utils/request.js` 导出对象 → `http.get/post/put/del(...)`

## 安全红线（改前跑 `scripts/security-baseline-check.js`，失败须 0）
- 身份只认 JWT；后台要求 token 与 `X-Admin-Id` 一致；验证**只走网关**
- 密钥零硬编码，yml 只写 `${XXX:}`；后台口令 BCrypt(cost12)
- 加密字段必须 `@TableName(autoResultMap=true)`；加密列不能等值 → `SearchHash`
- 任何 UGC 落库前必须过 `SecurityGuard#checkText`（带图再 `checkImages`），并加进 `content-security-check.js` 的 `ENTRIES`；`purify()` **返回 String 必须落返回值**
- 游客公开读只放 GET，网关正则必须精确，禁止宽泛放行 `/api/user`；`PRIVATE_SEGMENTS` 按**路径段**判私域
- 只过滤列表不够：详情接口必须单独 `assertVisible()`，抛 **404 而非 403**
- **账号准入在网关**：`AccountStateChecker` 60s 缓存校验 `t_user.status` / `token_version`（封禁 403、版本旧 401）；C 端 JWT 带 `tv`，**`JwtService.create` 没有 3 参数重载**（防漏传导致「刚登录就被踢」）
- **`X-Real-IP` 是唯一可信来源**：一律用 `ClientIp.resolve()`，禁止自己取 XFF 第一段；Nginx 必须覆盖式写（`$remote_addr`，不是 `$proxy_add_x_forwarded_for`）
- 异常根因只进日志：`campus.error.expose-detail` 生产 false

## 主题色（2026-10-28 定稿）
- **品牌主色＝蓝系 `#1E5FA8`**（原森林绿 `#006C49` 已全量换掉，不要再用绿色）。白字对比度 6.45:1
- 色阶：`--brand-50 #E8F0FA` / `100 #C7DDF3` / `200 #93C0E8` / `300 #5B97D1`（不可承载白字）/ `500 #1E5FA8` / `600 #17508F` / `700 #123F70`
- 页面渐变 `rgba(30,95,168,.28→0)` 叠白底；导航栏等效实色 **`#C0D2E7`**（alpha 不支持，须写实色）
- 换色走 `scripts/switch-theme-blue.js`（批量替换全站硬编码，含色阶与 custom-tab-bar 独立副本）；换主题时要同步 `app.json` 的 `navigationBarBackgroundColor` 与 **`tabBar.selectedColor`**，以及 `sync-navbar-theme.js` 的 `BRAND` 常量
- 语义色不参与换色：`danger #DC2626` / `warning #D97706` / `success #059669` / `like #FF4D7D`

## 高频踩坑（完整清单见 NOTES-engineering.md）
- **`java.lang.Error: Unresolved compilation problem`＝IDE 编译桩进了产物**，不是依赖问题：IDE（ECJ）编译失败仍写 `target/classes`，Maven 增量编译见 class 比 java 新就跳过 → 桩被打进 jar。解法只有 `mvn clean install`（**必须 clean**，关增量也不删旧桩）；parent pom 已设 `useIncrementalCompilation=false`。排查入口 `node scripts/check-build-artifacts.js`
- 本机环境变量 `SERVER__PORT`/`SERVER__HOST` 会抢端口，`java -jar` 前必须 `env -u SERVER__PORT -u SERVER__HOST`
- `scanBasePackages` 不能写 `com.campus`（会扫到 SecurityGuard 起不来）
- 网关放行 ≠ 服务内放行：`AdminSecurityConfig#excludePathPatterns` 必须同步加
- 上传大小三层互相掩盖：admin `multipart`(1MB) → 业务 `max-size`(5MB) → 网关 codec 8MB
- 「留空表示不修改」的字段禁止用 `@Size`（空串会被判长度不足 → 400）
- WXML 内联 `style` 会静默覆盖 wxss；部分 js 是 CRLF（`$` 不匹配 `\r` 前，正则静默失败）
- 小程序 `.json` 必须显式 `"usingComponents": {}`；跳分包必须配 `preloadRule`
- `visibility`（内容可见范围）≠ `scope`（浏览范围），禁止混用命名
- 展示名一律 `nickname`，绝不用 `real_name`
- 闲置/需求**只做信息展示**：禁止支付/下单/担保/评分；不抄电商话术（无数据＝虚假宣传）
- **小程序头部配色≠改组件**：tabBar 页（首页/功能区/消息/我的）用**微信系统导航栏**，配色只由 `app.json` `window.navigationBarBackgroundColor/TextStyle` 决定；`ui-navbar` 组件全站**没有任何页面在用**，改它无效。另有 17 个页面 json 单独覆盖导航栏色，改完全局要跑 `scripts/sync-navbar-theme.js` 同步
- **`navigationBarBackgroundColor` 不支持 alpha**（只认 `#RRGGBB`）：页面渐变用 rgba 半透明时，导航栏必须填「顶端 alpha 叠白底」的**等效实色**（如 `rgba(0,108,73,.28)`→`#B8D6CC`），换算 `255*(1-a)+C*a`，否则导航栏与页面顶端出现色阶断层。且**浅底必须配 `black` 字**（textStyle 只有 black/white，浅底白字不可读）—— 深浅与字色是联动的
- **改 `navigationBarTextStyle` 前先查 `navigationStyle:custom` 页**：custom 页自绘顶栏，若顶栏**不透明白底**（如群聊三页），跟随全局改 white 会白字落白底不可见 → 必须在其 json 显式钉回 `black`；顶栏透明的（消息页）才设 white
- **渐变看不见 ≠ 渐变没生效，先查页面自己的不透明容器**：容器级（`.feed`/`.list`/`.wrap`/`.topbar`/`.nav-scroll`/`.list-end` 等）写了白/灰底就会层层盖住 `page::after` 渐变（校园频道页 `pages/post/list.wxss` 就是 5 处叠出来整页纯白）。规则：**容器级一律 transparent，只有内容卡片才用白底**；按压态用 `rgba(255,255,255,.x)` 别用不透明白
- **主题换色必须 hex + rgba 都扫**：`scripts/switch-theme-blue.js` 只替换十六进制色时漏掉了 `rgba(0,108,73,α)`（阴影/浅底/描边大量用），18 文件 51 处残留绿。脚本已加 `RGBA_MAP`（只换 RGB、保留 alpha）
- **详情页/列表页一律「圆角卡片浮在渐变上」，禁止白底无缝拼接**：已按此标准改完 `pages/post/detail`（`.post-card` 包裹作者栏+正文）与 `pages/wall/detail`（`.article` 本身是单块，直接卡片化）。新增同类页面照抄这两页的参数：圆角 `--radius-lg` 24rpx + `--shadow-1` + `card-in` 320ms 入场
- **点赞/激活态动效优先用 `transform+transition` 而非 `@keyframes`**：keyframes 在 class 切到激活态时会播一次 → 进页时所有已激活项一起抖；transition 首帧不触发
- **页面背景渐变禁止用 `background-size:100% 100vh`**：它只铺满第一屏且随内容滚动 → 滚过一屏只剩白底（表现为「上下滑动背景变色」）。正解是 `page::after{position:fixed;inset:0;z-index:-1;pointer-events:none}` 视口固定层，`page` 只留 `background-color` 兜底。渐变顶端色必须**＝导航栏色**才无缝

## 自测脚本
`check-build-artifacts` / `check-miniprogram` / `content-security` / `security-baseline` / `db-schema-check` / `visibility-selftest` / `demand-selftest` / `promote-selftest` / `mock` / `home-zones` / `sync-navbar-theme`

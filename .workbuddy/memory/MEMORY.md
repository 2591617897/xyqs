# 项目长期约定

## 宿主平台（当前主端：微信小程序）
- `miniprogram/utils/util.js` 统一识别宿主并导出 `PLATFORM`（`wx`/`qq`），
  业务代码只用 `mp.*`，**不要直接判断 `wx`/`qq`**
- 登录链路带 `platform`：`POST /api/auth/login { code, platform, devOpenid }`
  - 后端配置：`campus.wx.appid/appsecret`（`WX_APPID`/`WX_APPSECRET`）、
    `campus.qq.appid/appsecret`（`QQ_APPID`/`QQ_APPSECRET`）、`campus.auth.mock`（`AUTH_MOCK`）
- **`t_user` 唯一键是 `uk_platform_openid (platform, openid)`**，不能改回单列 `openid`：
  openid 只在宿主平台内唯一。迁移脚本默认值给 `'qq'`（历史数据来自 QQ）
- 微信已废弃 `getUserInfo`/`getUserProfile` 静默授权：头像用 `open-type="chooseAvatar"`，
  昵称用 `input type="nickname"`。**chooseAvatar 给的是临时路径，必须先 upload 再入库**
- MySQL CLI：`/d/Program Files/MySQL/MySQL Server 8.0/bin/mysql`，
  中文乱码时加 `--default-character-set=utf8mb4`

## 环境参数（本地开发）
- 数据库：`guizhou_campus_fun`，账号 `root`，密码 `123456`
- MySQL `3306` / Redis `6379` / Nacos `8848`
- 网关 `8888`（唯一对外入口，路径前缀 `/api`）
- 服务端口：auth 8101 / user 8102 / goods 8103 / wall 8104 / post 8105 / message 8106
- 修改这些参数时必须同步的位置：
  `docker-compose.yml`、`backend/sql/schema.sql`(CREATE DATABASE)、`backend/sql/data.sql`(USE)、
  5 个 `campus-*/src/main/resources/application.yml`、`miniprogram/utils/config.js`(BASE_URL)、README、docs
- **Maven 必须指定 JDK 17**：`JAVA_HOME='C:\Program Files\Java\jdk-17'`，
  默认 JDK 会报「无效的目标发行版: 17」

## 工程约定
- 小程序端默认 `USE_MOCK = false`（`miniprogram/utils/config.js`，环境化配置：改 `ACTIVE_ENV` 或 `host/port` 即可，`SERVER/BASE_URL/UPLOAD_URL` 自动派生）
- **私信**：`campus-message`（8106，网关 `/api/messages/**`），表 `t_message_conversation`/`t_message`；
  `user_a=min(id)`、`user_b=max(id)`，`goods_id=0` 表示非商品会话；商品快照冗余进会话。
  **聊天页（pages/chat）不要加快捷话术 chips**——用户 2026-10-23 明确要求去掉底部快捷输入，只保留输入栏
- **后端父 POM 必须保留 maven-compiler-plugin 的 `-parameters`**：本项目未继承 `spring-boot-starter-parent`，
  缺了它所有 `@RequestParam/@PathVariable` 会报 "Name for argument ... not specified"（全站 400）。
  已在该 POM 显式开启，且 controller 注解也写死了 name（双保险）
- **启动顺序前置条件**：先 `cd backend && mvn -DskipTests clean install`。
  子模块单独 `mvn spring-boot:run` 时从本地仓库取 `campus-common`，没 install 会报
  "Could not resolve dependencies ... campus-common:jar:1.0.0"。
  本机本地仓库在 `D:\apache-maven-3.6.3\MavenData`（非默认 `~/.m2`）
- **`clean` 失败 = 有进程占用 target**（"Failed to delete ... target"）。此时若直接 `compile`，
  target 可能处于半损坏状态，会编译出 `LR;` 这种默认包类型的坏 class，启动报 `NoClassDefFoundError: R`。
  正确做法：先停服务，再 `mvn clean install`
- 在 WorkBuddy 执行环境里启动服务要 `env -u SERVER__PORT`，否则继承的 `SERVER__PORT=61581`
  会覆盖 `server.port`，所有服务抢同一端口；用户自己的终端/IDEA 无此变量
- **子模块 jar 是普通 jar，不是 fat jar**：父 POM 只在 `pluginManagement` 声明了
  spring-boot-maven-plugin，模块里也没写 `<executions>`，`repackage` 不会执行。
  所以 `java -jar target/xxx.jar` 会报「没有主清单属性」，**必须用 `mvn spring-boot:run` 启动**
- 改网关 `application.yml` 的路由后**必须重启网关**才生效（配置不是热加载）；
  判断路由是否生效：`curl /api/xxx` 返回 **404** = 没路由，返回 **401** = 已路由只差登录
- 演示数据对齐登录身份：`t_user` 里 9001（林小满）的 openid 就是 `config.js` 的 `DEV_OPENID`，
  开发者工具免真机登录即落到该账号，能看到种子商品/表白/帖子/私信。改种子数据时别破坏这个对应关系
- **同名 DTO 陷阱**：`campus-common` 与 `campus-user` 各有一个 `LoginOrCreateReq`
  （前者 Feign 契约，后者服务端接收），改字段时**两个都要改**
- **每个页面必须同时产出 4 个文件**：`.js` / `.wxml` / `.json` / `.wxss`；
  `.json` 里显式写 `"usingComponents": {}`——缺 json 会导致整页白屏并报
  `Cannot read properties of undefined (reading "usingComponents")`
- **require 相对路径按目录深度数层级**：`pages/user/space/space.js` 是 3 层深，
  引 utils 必须 `../../../utils/`（2 层页面才是 `../../utils/`）。写错不会报编译错，
  只在运行时模块加载失败 → Page 未注册 → **整页白屏只剩导航标题**，`node --check` 查不出
- **自绘顶栏页面必须 `navigationStyle: custom`**（如 pages/messages），否则原生导航栏
  和自绘顶栏叠两层（双返回键/双标题）。顶栏内联 `padding-top: {{statusBarHeight+8}}px`
  避开状态栏；功能图标用 SVG data-URI 对齐设计稿 Material 图标，**别用 ←/✓✓/☰ 等文本字符**
  （真机字体会变形，且常与设计稿图标不符，如设置应为 tune 滑块而非汉堡菜单）
- 校验命令：
  - `node scripts/check-miniprogram.js`（页面结构完整性）
  - `node scripts/mock-selftest.js`（模拟数据层，**139 条**断言，含 [12] 关注/主页、[13] 左滑删除段）
  - `node scripts/api-smoke-test.js`（后端 **45 项**核心接口连通性，**需 Nacos + 服务已启动**；
    **跑完要清理演示数据**：delete `content='冒烟测试消息'` 的行并重置 4 个会话的 last_*/unread_a）
- 统一响应 `{code,msg,data}`，`code=0` 成功；401 未登录 / 403 未认证 / 404 不存在
- **调用接口时不要再写 `/api` 前缀**：`utils/request.js` 内部是 `BASE_URL + url`，而 `BASE_URL`
  已经是 `SERVER + '/api'`，写 `'/api/posts'` 会变成 `/api/api/posts` → **404**（2026-10-24 就踩过，
  表现为「我的页计数 N、点进列表却是 0」，因为计数器走 `/user/me` 的 Feign 聚合、列表走前端请求）。
  正确写法：`http.get('/posts', { userId, page, size })`。唯一例外是 `config.js` 的 `UPLOAD_URL`（完整地址）
- **`Promise.all` 里每个请求都要各自 `.catch`**，否则一个 404/失败会把整页数据吞成空列表
- **作者名/头像不能只靠内容表里的快照**：`t_post.author_name`、`t_goods.seller_name` 都是发布时写的快照，
  用户改昵称/头像后列表会显示旧值（现象：个人主页头部是「莫东海」，卡片还是「校园用户4400」）。
  已在 `PostService#refreshAuthors` / `GoodsService#refreshSellers` 里用 `UserSnapshotMapper`（读 `t_user`）
  一次 IN 查询覆盖，快照仅在取不到时兜底。**新增内容类表也要照此处理**
- 子模块编译报「找不到符号 FollowClient / NotificationClient」= 本地仓库的 `campus-common` jar 过期，
  先 `mvn -pl campus-common -DskipTests install` 再编译其它模块
- **ID 一律以字符串传输**：雪花 ID 是 19 位 Long，超出 JS 安全整数（2^53-1）。
  `campus-common` 的 `JacksonConfig` 已把 `Long/long` 全局序列化为字符串；
  前端**不要对 id 做 `Number()`**（会丢精度 → 详情 404）。计数类是 `Integer`，仍是数字。
- 图片：后端存 `./uploads/yyyy/MM/`，返回绝对 URL；前端 `utils/img.js#resolve()` 统一收敛到当前 SERVER
- **静态资源映射必须用 `Path#toUri()`**：`"file:" + "C:/xxx"` 会得到少一个斜杠的 `file:C:/xxx`，
  Spring `PathResourceResolver` 解析失败 → 映射**静默失效** → 抛 `NoResourceFoundException` →
  被兜底 handler 吞成 500 JSON → 前端 `<image>` 显示灰块，**且因 HTTP 200 连 binderror 都不触发**
- `GlobalExceptionHandler` 的兜底 `@ExceptionHandler(Exception.class)` 会把静态资源 404 误报成 500，
  已单独处理 `NoResourceFoundException` 返回 404
- **调试 `<image>` 不显示的正确顺序**：先 `curl -D - -o file <图片URL>` 看 Content-Type 和文件头。
  `image/png` 才算正常；`application/json` 说明后端没返回图片（别去查微信配置）
- 首页频道与闲置的关系：**频道是帖子分区，商品不属于任何频道**。选中具体频道时不能混排闲置，
  否则切频道看到的还是同一批闲置，会被误判成「筛选没生效」
- **浏览范围（本校/本市）**：storage key `feedScope`（utils/scope.js 统一读写，首页/我的页/表白墙共用）；
  列表接口 `GET /posts`、`GET /wall/posts` 支持 `scope=school|city` + `city`（缺省=用户学校所在市），
  市域聚合依据 `t_school.city`（province/city/district 三列，district 见 migration-20261024-school-district.sql）。
  位置信息 API：`GET /auth/geo/city?lat&lng`（腾讯位置服务逆地理，key 走 `QQMAP_KEY`，
  未配置返回 available=false，前端必须回退学校所在市）。组件 `components/scope-sheet` 是切换弹层。

## 关注 / 粉丝与个人主页（2026-10-24 交付）
- **表**：`t_follow`（`uk_pair(follower_id,followee_id)` 唯一键是幂等基石、idx_followee、idx_follower）；
  `t_user` 冗余 `fans_count`/`follow_count`。迁移 `migration-20261025-follow-profile.sql`
- **接口**（均在 campus-user 8102，网关路由零改动）：
  `POST|DELETE /api/user/follow/{id}`（登录）、`GET /api/user/follow/status?userId=`（公开，返回布尔）、
  `GET /api/user/{id}/profile`（🔓 公开，未登录可读；封禁/不存在 404）
- **网关放行只能精确匹配**：`^/api/user/\d+/profile$`(GET) 与 `/api/user/follow/status`(GET 全等)。
  **禁止把 /api/user 加进宽泛公开前缀**——会误开 /user/me
- **计数事务铁律**：关系 insert/delete 与两条计数 UPDATE 同一 `@Transactional`；
  UPDATE 用 `greatest(x+delta,0)` 防负数；**DuplicateKeyException 的 catch 分支必须直接 return，
  绝不能让计数 UPDATE 漏进去**（并发双插会 +2 漂移）。粉丝数在 catch 内用
  `select fans_count ... for update` 加锁读最新已提交值（普通快照读会落后于并发事务）
- **主页聚合走 Feign 内部接口**：`/internal/goods/count`、`/internal/posts/count`（/internal/** 不经网关）；
  campus-user 已加 `@EnableFeignClients`，client 放 `com.campus.user.client`。
  **Feign 调用必须 try/catch 降级 0 + warn，不允许打崩主页**
- **前端**：`pages/user/space/space.*`（app.json 注册为 `pages/user/space/space`，四件套）；
  关注逻辑收敛 `utils/follow.js`，**不做 follow-btn 组件**（三处视觉形态与状态来源差异大）；
  乐观更新 + 失败回滚 + `followLoading` 防重
- **跳转一律 `catchtap`**（首页/闲置详情/帖子详情的头像+昵称区）——卡片本身是 bindtap 进详情，
  不 catch 会冒泡误跳
- 未纳入本期（P1/P2）：关注/粉丝列表页、消息中心 followers 汇总、「我的」页统计条真实化、
  互相关注标识、新粉丝通知、计数对账脚本

## 私信左滑删除 + 消息中心布局（2026-10-24 交付）
- **删除会话 = 只对当前用户隐藏，不删数据、对方不受影响**。
  表 `t_message_conversation_hide(user_id, conversation_id)`（`uk_user_conv` → 幂等）；
  `selectMine`/`countUnread` 用 `not exists` 排除。迁移 `migration-20261026-message-delete.sql`
- **撤回消息 = 软删除** `t_message.deleted`，仅发送者本人可删（他人 403、不存在 404）。
  **撤回会话最后一条时必须回滚会话摘要**（last_msg_id/last_content/last_time → 上一条存活消息），
  否则列表摘要会指向已撤回的消息
- **IM 语义**：删除后对方发来新消息 → 自动取消隐藏、会话重新出现；
  重新 `POST /messages/start` 也自动取消隐藏
- **接口**（campus-message 8106，网关 `/api/messages/**` 零改动）：
  `DELETE /api/messages/{id}`（删会话）、`DELETE /api/messages/{id}/messages/{msgId}`（撤回消息）
- **列表新增 `lastFrom`**（`me`/`peer`）用于显示「我：」前缀，批量查 `selectSenders`，
  **失败要 try/catch 降级**，不许打崩列表
- **左滑组件 `miniprogram/components/swipe-cell/`**（4 件套）：
  - 横向位移占优才接管手势（|dx|>|dy|），否则让位页面纵向滚动
  - `open` 属性**受控**：页面用 `openSwipeId` 保证同时只有一个展开；`openchange` 事件回传
  - **组件内 `overflow:hidden` 会裁掉卡片 box-shadow 且切掉气泡圆角** → 圆角/投影要放在**宿主元素 class** 上
  - **`scroll-into-view` 定位不到插槽内的 id**（shadow DOM）→ id 用 `swipe-id` 属性透传到组件根节点
  - **插槽里的 `catchtap` 属父组件作用域**（组件内 `onActionTap` 不触发）→ 父组件按钮直接 `catchtap` 父方法
- **聊天页只有「我发的」消息可左滑撤回**，对方消息走 `wx:else` 普通渲染（无左滑）
- 消息中心筛选改**本地过滤**（`allConvos` 全量缓存），不再每次筛选都打接口

## 登录 / 游客浏览 / 新用户引导（2026-10-24 交付）
- **游客可浏览是设计**：网关 `AuthGlobalFilter` 白名单 `/api/auth/`、`/api/schools`、`/uploads`；
  公开读 = `GET` + 前缀 `/api/goods`、`/api/wall`、`/api/posts`、`/api/channels`
  （排除含 `/mine`、`/orders`、`/favorite`）+ 精确 `GET /api/user/{id}/profile`。其余需 JWT
- **前端没有启动即登录**：`app.js onLaunch` 只读缓存；`auth.ensureLogin()` 只在交互点触发
  （点赞/关注/评论/发布/我的/认证，共 12 处）。`wx.login` 静默无授权弹窗；
  `getUserInfo/getUserProfile` 静默授权已废弃，头像昵称必须 `chooseAvatar` + `input type=nickname`
- **建号默认值**：`UserService.loginOrCreate` → 昵称「校园用户XXXX」、`schoolId=null`、`verifyStatus=0`
- **`schoolId` 为 null 会让「本校」筛选失效**：列表服务写的是
  `.eq(schoolId != null, Post::getSchoolId, schoolId)`，为空时**不加过滤** → 看到全站内容。
  任何新增列表过滤都要注意这个退化分支
- **`PUT /user/me` 的 `schoolId` 只给未认证用户用**（新用户引导）：`verifyStatus==2` 抛
  「已完成学籍认证，如需更换学校请重新提交认证」，防绕过学生证审核；换校仍走 `/user/verify`
- **改 `schoolId` 必须重签 token**：schoolId 是登录时写进 JWT 的快照，网关只信 token 里的
  `X-School-Id`。流程：`PUT /user/me` → `auth.ensureLogin(true)` 重签 → `auth.updateUser()`
  回填 `schoolName/schoolCity`（`UserBrief` 不含 schoolName，不 merge 会把胶囊打回「选择学校」）
- **引导组件 `components/school-onboard/`**：首页 `onShow` → `checkOnboard()`；
  选完写 storage 无跳过标记才弹，点「暂不选择」写 `onboardSkipped=1`；
  `openScope` 在无 schoolId 时改为打开它；z-index 1100（高于 scope-sheet 的 1000）
- **id 类型陷阱**：后端雪花 id 是字符串、mock 是数字 → 组件内统一 `_key = String(id)`
  再做选中态比较，否则高亮不生效
- **多微信同号的三个坑，一个都不能再踩**（2026-10-24 已修）：
  1. `campus.auth.mock` 默认必须是 **false**（yml `${AUTH_MOCK:false}` + Java `@Value` 默认值要同步改）；
  2. `code2Session` **禁止** `if (mock || !hasText(appid))` 这种静默降级 —— appid/appsecret 缺失要直接报错；
  3. 前端 `devOpenid` **只能在 `config.js` 的 `DEV_LOGIN===true` 时发送**（默认 false）。
  mock 分支会把它当 openid 原样入库，一旦常态发送，所有微信号都会写到同一行 `t_user`。
- 看种子数据（林小满 9001）需要 `AUTH_MOCK=true` **且** `DEV_LOGIN: true`，两个同时开才会合号；
  真实登录下 9001 不可达（它的 openid 就是 `dev_openid_guizhou_campus_001`）
- `WX_APPID` 已内置默认 `wx2cac838bfcc963d7`；`WX_APPSECRET` 必须走环境变量，**不要写进仓库**
- **小程序凭据实际落在 `campus-auth/src/main/resources/application-local.yml`**（文件名命中 `.gitignore`
  第 116 行 → 不进仓库），由 `application.yml` 的 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`
  默认激活；profile 配置优先级高于主配置。换机器/新克隆需重建该文件，缺失时登录报「微信登录未配置」
- **凭据有效性自检**（别等真机）：`GET https://api.weixin.qq.com/sns/jscode2session?appid=..&secret=..&js_code=test&grant_type=authorization_code`
  → `40029 invalid code` = 有效；`40125` = secret 错；`40013` = appid 错
- **腾讯位置服务 key 若开了「签名校验」**：WebService 返回 `status=111`（签名验证失败）。请求必须带
  `sig = MD5(path + "?" + 参数按名升序拼接 + SK)`。后端已支持：`campus.geo.qqmap-sk`（环境变量 `QQMAP_SK`），
  留空则不带 sig。**签名串里的值只能做最小化 URL 编码**（`,` `:` `-` `.` `_` `~` 保留），
  用 `URLEncoder` 会把 `location=39.98,116.30` 的逗号变成 `%2C`，签名必错
- **`jscode2session` 响应头是 `text/plain`**（body 才是 JSON）→ 用 `RestTemplate.getForObject(url, Map.class)`
  会抛 `UnknownContentTypeException`。必须 `String.class` 取回再 `ObjectMapper` 解析（`AuthService.code2Session` 已这么写）。
  任何新增的第三方 JSON-over-text/plain 接口都按这个来
- **「绑定学校」与「学籍认证」是两件事，别混文案**：绑定 = `schoolId != null`；认证 = `verifyStatus == 2`。
  2026-10-24 修过一次：我的页兜底写成 `{{user.schoolName || '已绑定学校'}}` → 没学校时反而显示「已绑定学校」。
  **判断一律以 `schoolId` 为准**，姓名 `schoolName` 只用于显示，查不到名时回退「已选择学校」
- 未认证用户可选学校入口：首页引导弹层 + 我的页 `🎓 未绑定学校 去选择 ›`（都复用 `components/school-onboard`）；
  已认证用户换校必须走 `pages/user/verify`
- **JWT 无状态 → 清库后旧 token 仍然「有效」**，服务端只是查不到用户（`/user/me` 404「用户不存在」）。
  前端必须自愈：`utils/request.js` 对 `code===404 && url 以 /user/me 开头` 做 `cleanLogin()`，
  再由 `auth.relogin()` 重建会话；**判定条件必须锁死 `/user/me`**，否则「看已删用户主页」会把浏览者踢下线。
  后端 `requireUser` 只在行不存在时返回 404（封禁用户仍返回行），所以不会出现 404→重登→404 死循环，但仍用
  `_reloginTried` 标记兜一层
- **没有 schoolId 时不要用 feedScope**：残留的 `city` 会让胶囊显示成「本市/某市 · 全市」，看起来像学校不对。
  `applyUser/applyScope` 无学校时强制按「选择学校」渲染；绑定学校成功后 `scopeMod.write({scope:'school'})` 回本校
- 我页显示「已绑定学校 / 未绑定学校」与胶囊显示「城市 · 全市」是两回事，别混；胶囊文本 = `scopeMod.label()`

## 布局铁律（踩过坑）
- **`scroll-view` 放进 flex 列里必须写 `min-height: 0`**：flex 子项默认 `min-height: auto` 会被内容撑到全高，
  scroll-view 拿不到受限高度就**不会滚动**（表现：弹层里的列表「滑不动」）。参考 `pages/chat/index.wxss .msgs`、
  `pages/user/verify.wxss .school-scroll`。用 `max-height` 单独的（`order-scroll`、`school-onboard/.ob-list`）也可行
- **弹层容器写死 `height: Xvh` 时必须配 `box-sizing: border-box`**，否则 Xvh + padding 超屏、底部被顶出可视区
- 横向 `scroll-view`（`nav-scroll` / `cat-scroll` / `filter-scroll`）不需要高度约束

## 图片加载（真机灰块）
- **`<image>` 与 `wx.previewImage` 是两条下载通道**：真机上 `http://192.168.x.x` 的 `<image>` 会**静默失败**
  （灰块、`binderror` 也可能不触发），但同一个 URL 用 `previewImage` 能正常打开。
  「点开大图能看、列表里全是灰块」= 这个现象，**不是图片地址或代码问题**
- 「真机调试」模式下 `<image>` 有已知不加载问题 → 先用**「预览」**二维码验证，能区分工具限制与真问题
- 局域网 IP 无法加入 `downloadFile` 合法域名 → 真机看图必须 **HTTPS 域名**（内网穿透或正式域名），
  填 `utils/config.js` 的 `tunnel` / `prod`，切 `ACTIVE_ENV`，图片地址随 `SERVER` 全站生效
- 服务端自检：`curl -D - -o /dev/null <图片URL>` 必须 `200` + `Content-Type: image/jpeg`；
  返回 `application/json` 说明没落到静态资源映射

## 「同校 / 同市」标签口径（唯一口径，别再写死）
- 判定：`String(myUser.schoolId) === String(item.schoolId) ? '同校' : '同市'`（`myUser` 取
  `globalData.user` 再回退 `storage.user`）；任一 schoolId 为空则**不显示**标签，不要瞎标
- 已统一的位置：首页信息流卡片（`index.js` 的 `scopeBadge`，仅全市模式打标）、帖子详情（`post.schoolBadge`）、
  表白墙详情（`post.sameSchool` → 「仅同校可见」/「同城可见」）
- 注意 `POST /api/posts/{id}` 返回的 `schoolId` 是**字符串**（Long 全局序列化为 String），比较前统一 `String()`
- 排查登录问题看 `campus-auth` 日志：`登录 platform=wx openid=xxxx****xxxx`
  —— 两个微信号应当打印出两个不同 openid

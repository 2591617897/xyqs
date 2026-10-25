# 项目长期约定（压缩版 · 2026-10-25 整理）

## 宿主平台
- `utils/util.js` 导出 `PLATFORM`（wx/qq），业务只用 `mp.*`，不要直接判断 `wx`/`qq`
- 登录带 platform：`POST /api/auth/login {code, platform, devOpenid}`；配置 `campus.wx/qq.appid|appsecret`、`campus.auth.mock`
- **`t_user` 唯一键 `uk_platform_openid(platform, openid)`**（openid 只在平台内唯一），别改回单列
- 微信已废弃 `getUserInfo/getUserProfile`：头像用 `open-type="chooseAvatar"`（临时路径，先上传再入库），昵称用 `input type="nickname"`
- MySQL CLI：`/d/Program Files/MySQL/MySQL Server 8.0/bin/mysql`，乱码加 `--default-character-set=utf8mb4`

## 环境参数
- 库 `guizhou_campus_fun` / root / 123456；MySQL 3306、Redis 6379、Nacos 8848；网关 8888（唯一入口，前缀 `/api`）
- **线上域名：`https://campus-api.trkcwl.com`**（主域名 trkcwl.com，**该服务器跑多个项目 → 子域名必须带项目名**）。
  小程序 `config.js` 的 `prod` 已填，上线把 `ACTIVE_ENV` 切成 `'prod'`；
  Nginx 模板 `deploy/nginx-campus-api.trkcwl.com.conf`（443 → 8888，upstream 名 `campus_gateway` 带项目前缀防冲突）
- **上域名后必须设 `UPLOAD_BASE_URL=https://campus-api.trkcwl.com`**，否则 `UploadController` 会按
  `campus.upload.base-url` 的内网默认值拼图片地址；历史绝对地址由前端自动归一，不用迁数据
- **图片地址只在 `utils/request.js` 归一，页面别再各自处理**：成功回调统一 `resolve(normalizeImages(body.data, 0))`，
  递归把含 `/uploads/` 的字符串换成当前 `SERVER`。之前靠各页面调 `resolve`，**`avatar` 系列全漏了** →
  头像请求打到 `192.168.0.102` 成灰块。页面里再调 `resolve` 幂等无害，新增字段无需额外处理
- **「上传成功、库里有地址，但图片 404」**：`curl -D - <URL>` 若看到 **200 + `{"code":404,"msg":"资源不存在"}`**
  （Spring 的 JSON 被 `<image>` 当图片解码失败）→ 是「写 A 目录、读 B 目录」。
  根因两选一：`campus.upload.dir` 用了相对 `./uploads`（进程工作目录变了）或 **Nacos 残留多个 campus-user 实例**。
  看启动日志 `[upload] 静态资源映射 /uploads/** -> file:/...` 即可确认读取目录。
  **生产让 Nginx alias 直出 `/uploads/`**（模板已启用，服务器实际路径 `/mnt/ssd/web/campus-api.trkcwl.com/uploads/`），
  `UPLOAD_DIR` 必须给绝对路径
- **`./uploads` 相对的是进程工作目录（cwd），不是 jar 所在目录** —— systemd `WorkingDirectory` /
  `cd && java -jar` / 从别处 `nohup java -jar` 三种 cwd 完全不同。已用 `UploadPaths` 统一：
  写（`UploadController`）与读（`WebConfig`）同一套解析，jar 运行时按 **jar 同目录**，IDE 时退回 cwd
- 微信后台要分别配 `request` / `uploadFile` / `downloadFile` 三个合法域名，不能带端口/路径/IP，且必须 ICP 备案
- 端口：auth 8101 / user 8102 / goods 8103 / wall 8104 / post 8105 / message 8106
- 改端口或库名要同步：`docker-compose.yml`、`sql/schema.sql`、`sql/data.sql`、5 个 `application.yml`、`utils/config.js`、README、docs

## 构建 / 启动
- **Maven 必须 JDK 17**：`JAVA_HOME='C:\Program Files\Java\jdk-17'`；本地仓库在 `D:\apache-maven-3.6.3\MavenData`
- 先 `cd backend && mvn -DskipTests clean install`，否则子模块取不到 `campus-common`
- 子模块是**普通 jar 非 fat jar** → 只能用 `mvn spring-boot:run`，`java -jar` 报「没有主清单属性」
- `clean` 失败 ＝有进程占用 target；此时别直接 compile（会产出坏 class，启动报 `NoClassDefFoundError: R`），先停服务
- 在 WorkBuddy 里启动要 `env -u SERVER__PORT`（继承的 61581 会覆盖 server.port）
- 编译报「找不到符号 XxxClient」＝本地 common jar 过期 → `mvn -pl campus-common -DskipTests install`
- 改网关路由**必须重启网关**；`curl /api/xxx` 404=没路由，401=已路由只差登录
- 父 POM 必须保留 `-parameters`（未继承 spring-boot-starter-parent），否则 `@RequestParam` 全站 400

## 后端通用
- 统一响应 `{code,msg,data}`，0 成功；401 未登录 / 403 未认证 / 404 不存在
- **ID 一律字符串传输**（雪花 19 位超 JS 安全整数），`JacksonConfig` 已全局把 Long 序列化为 String；前端不要 `Number(id)`
- 前端请求**不要再写 `/api` 前缀**：`BASE_URL` 已含 `/api`（唯一例外 `config.js` 的 `UPLOAD_URL`）
- `Promise.all` 每个请求各自 `.catch`，否则一个失败吞掉整页
- 作者名/头像不能只靠内容快照：`PostService#refreshAuthors`、`GoodsService#refreshSellers` 用 `UserSnapshotMapper` 覆盖；新增内容表照办
- 静态资源映射必须 `Path#toUri()`（`"file:" + "C:/x"` 少斜杠会静默失效 → 500 JSON → 灰块）
- `GlobalExceptionHandler` 已单独把 `NoResourceFoundException` 返回 404（别被兜底 Exception 吞成 500）
- 内部计数（绕过网关直连）：`GET :8105/internal/posts/count`、`GET :8104/internal/wall/posts/count?category=`

## 计数口径
- **同一指标只允许一个口径**：「我的动态」= 帖子 + 表白墙全部类别（后端 `UserVO.dynamicCount` 算好）。前端勿自行相加（会漏 find/findp）
- 关注/粉丝数：`t_user.fans_count`/`follow_count` 冗余，关系表操作与两条 UPDATE 同事务，`greatest(x+delta,0)` 防负

## 关注 / 粉丝 / 互关（2026-10-25 增补）
- 表 `t_follow`：`uk_pair(follower_id, followee_id)`；迁移 `migration-20261025-follow-profile.sql`
- 接口：`POST|DELETE /api/user/follow/{id}`、`GET /api/user/follow/status?userId=`、`GET /api/user/follow/list?type=following|fans`、`GET /api/user/{id}/profile`（公开）
- **`FollowUserVO`/`ProfileVO` 都返回 `followedBy`（对方是否关注我）**；前端 `mutual = isFollowing && followedBy` → 按钮显示「相互关注」
- 前端把 `followedMe` 缓存在卡片上：取消关注再回关时互关状态能还原，不必重拉接口
- `DuplicateKeyException` 的 catch 必须直接 return（不能让计数 UPDATE 漏进去，双插 +2 漂移）；粉丝数用 `select ... for update` 读
- 网关公开只能**精确匹配**：`^/api/user/\d+/profile$`、`/api/user/follow/status`。**禁止宽泛放行 `/api/user`**（会误开 /user/me）
- 跳转一律 `catchtap`（头像/昵称区），否则冒泡进详情
- 未做（P1/P2）：新粉丝通知、计数对账脚本、消息中心 follower 汇总

## 私信（campus-message 8106）
- 会话 `t_message_conversation`（`user_a=min(id)`/`user_b=max(id)`，`goods_id=0`=非商品）、消息 `t_message`
- 删会话＝**只对自己隐藏**（`t_message_conversation_hide`，uk 幂等，`selectMine/countUnread` 用 not exists 排除）；对方发新消息／重新 start 自动取消隐藏
- 撤回＝软删 `t_message.deleted`，仅本人；**撤回最后一条要回滚会话摘要**
- 列表 `lastFrom`(me/peer) 批量查 `selectSenders`，失败 try/catch 降级
- 左滑组件 `components/swipe-cell`：横向占优才接管手势；`open` 受控（页面 `openSwipeId`）；圆角/投影放宿主 class（组件 `overflow:hidden` 会裁掉）；插槽内 id 用 `swipe-id` 透传（`scroll-into-view` 进不了 shadow DOM）；插槽按钮直接 `catchtap` 父方法
- 聊天页只有「我发的」可左滑撤回；**不要加快捷话术 chips**（用户 2026-10-23 明确要求）

## 登录 / 游客 / 引导
- 游客可浏览是设计；公开读 = GET + `/api/goods|wall|posts|channels`（排除 mine/orders/favorite）+ 精确 profile
- 前端不在启动时登录；`auth.ensureLogin()` 只在交互点调用，且**无 token 时先 `confirmLogin()` 征得同意**，拒绝绝不建号；`ensureLogin(true)` 仅用于明确意图
- 建号默认：昵称「校园用户XXXX」、schoolId=null、verifyStatus=0
- **`schoolId=null` 会让「本校」筛选退化**（`.eq(cond, ...)` 不加过滤 → 看全站）
- 「绑定学校」= schoolId != null；「学籍认证」= verifyStatus == 2，**文案别混**。`PUT /user/me` 的 schoolId 只给未认证用户
- **JWT 里 schoolId/vs 是登录快照** → 改库后必须重签：`ensureLogin(true)` 或 `/user/verify` 返回的新 token + `markTokenFresh()`
- 症状对照：页面显示已认证但写操作 403 / 本校筛选失效 = token 快照过期
- **JWT 无状态**：清库后旧 token 仍"有效"，`/user/me` 404 → `request.js` 只对 `code===404 && url 以 /user/me 开头` 做 `cleanLogin()`（锁死条件，否则会误踢浏览者）
- 多微信同号三坑：`campus.auth.mock` 默认 false；`code2Session` 不许静默降级；`devOpenid` 只在 `DEV_LOGIN===true` 时发送
- 看种子数据（9001 林小满）需 `AUTH_MOCK=true` **且** `DEV_LOGIN: true`
- 小程序凭据在 `campus-auth/src/main/resources/application-local.yml`（gitignore，默认 profile=local）
- 凭据自检：`js_code=test` 打 `jscode2session` → 40029=有效 / 40125=secret 错 / 40013=appid 错
- `jscode2session` 响应头是 `text/plain` → 必须 `RestTemplate.getForObject(url, String.class)` 再 ObjectMapper
- 腾讯位置服务开签名校验时返回 111，需 `sig=MD5(path?params+SK)`（`campus.geo.qqmap-sk`）；签名串值只做最小编码（`,` 不能变 `%2C`）
- **IP 定位**：`GET /api/auth/geo/ip-city`（后端读 XFF/X-Real-IP → `/ws/location/v1/ip`，城市在 `result.ad_info`）；
  选学校用它推荐同城（`utils/school.js` 的 `ipCity/buildList`），**不用 wx.getLocation**（要授权+requiredPrivateInfos）。
  结果后端缓存 6h/512 个 IP —— 腾讯 key **每日配额很小**（实测 121 超限），上线要申请配额

## 布局铁律
- `scroll-view` 在 flex 列里必须 `min-height: 0`，否则不滚动（`chat/.msgs`、`verify/.school-scroll`）
- **父容器必须是「定高」`height`，不能只给 `max-height`**：`max-height` 是内容驱动，里面 `flex:1` 的
  scroll-view 会拿到「内容高度」→ 既不滚动也不裁剪，列表直接盖住底部按钮。踩过：`school-onboard` 的
  `.ob-sheet` 原是 `max-height:86vh`，学校一多就滑不动；改成 `height:78vh` 立刻正常。
  可用的两种写法：① 弹层定高（verify 76vh / onboard 78vh）；② **scroll-view 自己带 `max-height`**（`user/.order-scroll`）
- **自定义 tabBar 压不住弹层**：`custom-tab-bar/` 是框架层渲染，页面 z-index 再高（mask 1100 vs tabbar 999）也盖不过，
  弹层底部会被底栏切掉。必须由页面 `getTabBar().setData({hidden:true})` 隐藏、关闭时恢复，
  并在 `onShow` 里 `hidden:false` 兜底。范式见 `pages/wall/index.js#toggleTabBar`，
  首页/我的页的选学校与浏览范围弹层已补
- 弹层写死 `height:Xvh` 必须配 `box-sizing: border-box`
- 底部弹层输入框要手动避键盘：`adjust-position="{{false}}"` + `bindkeyboardheightchange` → `bottom: {{keyboardHeight}}px`

## 图片
- `<image>` 与 `previewImage` 是两条下载通道：真机 `http://192.168.x.x` 的 `<image>` 静默失败（灰块、binderror 不触发），previewImage 正常 → 不是代码问题
- 「真机调试」模式 `<image>` 有已知问题，先用「预览」验证；局域网 IP 加不进 downloadFile 白名单 → 真机看图要 HTTPS
- 排查顺序：`curl -D - -o f <url>`，`Content-Type: image/png|jpeg` 才对；`application/json` = 后端没返回图片

## 同校/同市标签
- `String(myUser.schoolId) === String(item.schoolId) ? '同校' : '同市'`，任一为空不显示
- 已统一：首页信息流（仅全市模式打标）、帖子详情、表白墙详情（`sameSchool`）

## 页面文件规范
- 每页 4 件套；`.json` 必须写 `"usingComponents": {}`（缺了整页白屏）
- require 相对路径按深度数层级（`pages/user/space/space.js` = `../../../utils/`），写错不报编译错、只白屏
- 自绘顶栏必须 `navigationStyle: custom`；图标用 SVG data-URI，别用 ←/✓/☰ 文本字符

## 校验脚本
- `node scripts/check-miniprogram.js`（结构完整性）
- `node scripts/mock-selftest.js`（模拟层 **139** 断言）
- `node scripts/api-smoke-test.js`（后端 **45** 项，需 Nacos + 服务已启动；**跑完清演示数据**：删 `content='冒烟测试消息'` 并重置 4 个会话 last_*/unread_a）

## 演示数据
- `config.js` 的 `DEV_OPENID` 对应 t_user 9001（林小满），改种子数据别破坏这个对应（真实登录下 9001 不可达）
- 显式 id 全字符串透传，`_key = String(id)` 再做选中态比较

## 其他
- 首页频道是**帖子分区**，商品不属于任何频道；选具体频道时不能混排闲置
- 浏览范围：storage `feedScope`（`utils/scope.js`），`GET /posts|/wall/posts` 支持 `scope=school|city` + `city`；无 schoolId 时不要用残留 scope，胶囊按「选择学校」渲染
- `campus-common` 与 `campus-user` 各有一个 `LoginOrCreateReq`，改字段两个都要改

## 接口路径 / 返回结构（踩过坑，别再犯）
- **学校字典是 `/schools`，不是 `/auth/schools`**：`AuthController` **无类级 `@RequestMapping`**，
  只有 `@PostMapping("/auth/login")` 与 `@GetMapping("/schools")`。写 `/auth/schools` 网关直接 404
- **`GET /user/follow/list` 返回裸数组**（`R<List<FollowUserVO>>` → request.js 解包后是 Array），
  **不是 `{list:[]}`**。一律 `Array.isArray(res) ? res : ((res && res.list) || [])` 读

## 视觉 / UI 重构（2026-10-25 拍板，进行中）
- 决策：**自建 `ui-*` 轻量组件库，不引入 Vant / TDesign / WeUI**（项目无 package.json、无 CI，
  且已有 84 个公共 class / 渐变 Hero / CSS 画形头像 / 自研 swipe-cell 都无第三方对应物）
- **主色森林绿 `--brand-500 #006C49`**（白字对比度 6.48 ✅；原 `#FF8FA8` 只有 2.15，是硬指标不合格）。
  樱花粉降级为**情感子色** `--like-500 #FF4D7D`，只服务表白墙 / 点赞 / 收藏
- token 单一来源 `styles/tokens.wxss`、图标 `styles/icons.wxss`，`app.wxss` 顶部 `@import`
- 图标统一 **SVG data-URI + `-webkit-mask-image` + `background-color:currentColor`**（可变色、
  ~16KB、无字体机型差异）；命名 `ico-{语义}[-fill|-o]`，**尺寸不进 class**，走 ui-icon 的 `size`
- ⚠️ **`page{}` 作用域的 CSS 变量对 `custom-tab-bar` 失效**（不在页面 DOM 树内）→ 必须在
  `custom-tab-bar/index.wxss` 顶部**冗余写一份 token 副本**，否则底栏变黑
- ⚠️ 所有 `var()` **必须带 fallback**（`--mac-cream` 未定义且无 fallback，已静默失效一次）
- 阴影只留 3 个 token（原 39 套）：`--shadow-1/2/3` 全部 `rgba(15,23,42,…)` 冷中性
- 卡片圆角统一 `--radius-lg 24rpx`；弹层高度统一 `--sheet-max-h 78vh`（原 7 档）；
  tabbar 预留统一 `--tabbar-height 120rpx`（原 170/180/190 三档不一致）
- `pages/user/index.wxss` 的 23 个 `--mac-*` 局部变量覆盖了 9 个全局同名变量 → 必须删

# 项目长期约定（2026-10-25 三次整理）

## 环境 / 基础设施
- 库 `guizhou_campus_fun`/root/123456；MySQL 3306、Redis 6379、Nacos 8848、网关 8888（唯一入口，前缀 `/api`）
- 端口：auth 8101 / user 8102 / goods 8103 / wall 8104 / post 8105 / message 8106 / admin 8107
- 线上 `https://campus-api.trkcwl.com`（服务器多项目 → 子域名带项目名）；`ACTIVE_ENV='prod'`；Nginx 模板 `deploy/nginx-campus-api.trkcwl.com.conf`（443→8888，upstream `campus_gateway`）
- 上域名必须设 `UPLOAD_BASE_URL=<域名>`；上传目录必须**绝对路径**（`UploadPaths` 统一写读，`./uploads` 随 cwd 漂移）；生产 Nginx alias 直出 `/uploads/`（`/mnt/ssd/web/campus-api.trkcwl.com/uploads/`）
- 图片地址只在 `miniprogram/utils/request.js` 归一（递归替换含 `/uploads/` 的字符串），页面别各自处理
- 「上传成功但 404」→ `curl -D -`：`200 + code:404` = 写 A 读 B（相对路径 / Nacos 多实例）；看启动日志 `[upload] 静态资源映射`
- 微信后台分别配 `request`/`uploadFile`/`downloadFile` 三域名（无端口路径 IP，需备案）；改端口/库名要同步 compose、schema.sql、data.sql、5 个 yml、config.js、README、docs

## 构建 / 启动
- Maven 需 JDK 17（`JAVA_HOME='C:\Program Files\Java\jdk-17'`，仓库 `D:\apache-maven-3.6.3\MavenData`）；先 `cd backend && mvn -DskipTests clean install`
- 子模块是普通 jar → 只能 `mvn spring-boot:run`（`java -jar` 报无主清单）；`clean` 失败＝进程占用 target，先停服务（否则坏 class → `NoClassDefFoundError: R`）
- WorkBuddy 内启动加 `env -u SERVER__PORT`；「找不到符号 XxxClient」＝common jar 过期 → `mvn -pl campus-common -DskipTests install`
- 改网关路由**必须重启网关**（404=无路由，401=已路由只差登录）；父 POM 必须留 `-parameters`（否则 `@RequestParam` 全 400）

## 后端 / 前端通用约定
- 统一响应 `{code,msg,data}`，0 成功；401 未登录 / 403 未认证 / 404 不存在
- **ID 一律字符串传输**（雪花 19 位），`JacksonConfig` 全局 Long→String；前端别 `Number(id)`
- 前端请求不要再写 `/api` 前缀（`BASE_URL` 已含；唯一例外 `config.js` 的 `UPLOAD_URL`）
- `Promise.all` 每个请求各自 `.catch`，否则一个失败吞整页
- 作者名/头像不能只靠快照：`PostService#refreshAuthors`、`GoodsService#refreshSellers` 用 `UserSnapshotMapper` 覆盖，新内容表照办
- 静态资源映射必须 `Path#toUri()`（拼 `"file:"+"C:/x"` 静默失效 → 500 → 灰块）

## 平台 / 登录 / 游客
- 业务只用 `utils/util.js` 的 `mp.*`，禁止直接判 `wx`/`qq`；登录 `POST /api/auth/login {code, platform, devOpenid}`
- `t_user` 唯一键 **`uk_platform_openid(platform, openid)`**；头像 `open-type="chooseAvatar"`（先上传再入库）+ 昵称 `input type="nickname"`
- 游客可浏览是设计；公开读 = GET + `/api/goods|wall|posts|channels`（排除 mine/orders/favorite）+ 精确 profile
- 不在启动时登录；`ensureLogin()` 无 token 先 `confirmLogin()` 征得同意，拒绝绝不建号；`ensureLogin(true)` 只给明确意图
- 建号默认：昵称「校园用户XXXX」、schoolId=null、verifyStatus=0
- **`schoolId=null` 让「本校」筛选退化**（不加过滤＝看全站）→ 所以有选学校引导
- 「绑定学校」= schoolId != null；「学籍认证」= verifyStatus == 2，文案别混；`PUT /user/me` 的 schoolId 只给未认证用户
- **JWT 的 schoolId/vs 是登录快照** → 改库后必须重签（`ensureLogin(true)` 或 `/user/verify` 新 token + `markTokenFresh()`）
- `request.js` 只对 `code===404 && url 以 /user/me 开头` 做 `cleanLogin()`（锁死条件，防误踢浏览者）
- 凭据在 `campus-auth/.../application-local.yml`（gitignore）；`js_code=test` 自检：40029 有效 / 40125 secret 错 / 40013 appid 错
- `jscode2session` 响应是 `text/plain` → `getForObject(url, String.class)` 再 ObjectMapper
- 腾讯位置服务签名 `sig=MD5(path?params+SK)`；`GET /api/auth/geo/ip-city`（未选学校推荐同城），**key 日配额小，上线要申请**

## 小程序页面结构（tabBar / 首页 / 功能区）
- tabBar 4 个真实页 + 中间凸起发布钮：`index` / `zone` / `messages` / `user`；**`wall/index`、`goods/list` 已移出 → 只能 navigateTo，禁止 switchTab**
- 「功能区」= `pages/zone/index`，入口只来自 `utils/zones.js#buildEntries`（3 固定 + t_channel 动态）
- 点「校园闲置」入口要清 `app.globalData.goodsMode`，否则落在「我的闲置/收藏」筛选态
- 首页 = 功能区布局（**无频道 tab、无金刚区**）：顶栏 → 公告位 → 今日最热/我想说/校园闲置/校园公告/校园生活
- 内容区一律**单列全宽**（`.zone-list` 纵向 flex，`.zcard` 100%），不要再写横向 scroll-view + 固定宽度卡
- 每区最多 3 张卡，由 `ZONE_LIMIT`/`take()` 收敛，其余走「更多 ›」；改布局要同步 `zones.js` 与两套自测断言
- 闲置卡是左图右文整行：图 200rpx、右侧 `flex:1`、价格 `margin-top:auto`
- 公告区(2001) 与生活区按 channelId 拆同一次 `/posts` 响应；**空区不渲染**；频道 2002 已下线（`migration-20261025-home-zones.sql`）
- 点赞按 kind 分流；表白墙派生字段在 `utils/wall.js`
- custom-tab-bar 自带未读角标（`attached` + `pageLifetimes.show` 拉 `/messages/unread`，无 token 归 0）
- ⚠️ 自定义 tabBar 压不住弹层：页面必须 `getTabBar().setData({hidden:true})`，`onShow` 里 `hidden:false` 兜底
- `getTabBar()` 只有 tab 页有；非 tab 页拿到 undefined

## 布局 / 图片铁律
- `scroll-view` 在 flex 列里必须 `min-height: 0`；**父容器要定高 `height` 不能只给 `max-height`**；弹层写死 `height:Xvh` 必须 `box-sizing: border-box`
- 底部弹层输入框避键盘：`adjust-position="{{false}}"` + `bindkeyboardheightchange` → `bottom: {{keyboardHeight}}px`
- tab 页底部留 ~110rpx + 安全区，非 tab 页不留
- `<image>` 与 `previewImage` 是两条通道：真机 `http://192.168.x.x` 的 `<image>` 静默失败（灰块、binderror 不触发）；「真机调试」模式有已知问题，先用「预览」；局域网 IP 加不进 downloadFile 白名单 → 真机看图要 HTTPS
- 查图顺序 `curl -D - -o f <url>`：`Content-Type: image/*` 才对，`application/json` = 后端没返图
- 页面 4 件套；`.json` 必须显式 `"usingComponents": {}`（缺了整页白屏）；require 相对路径按深度数层级；自绘顶栏必须 `navigationStyle: custom`，图标用 SVG data-URI

## 接口坑（踩过别再犯）
- **学校字典是 `/schools` 不是 `/auth/schools`**（`AuthController` 无类级 `@RequestMapping`）
- **`GET /user/follow/list` 返回裸数组** → `Array.isArray(res) ? res : (res?.list || [])`
- 网关公开只能**精确匹配**（`^/api/user/\d+/profile$`、`/api/user/follow/status`），禁止宽泛放行 `/api/user`
- 跳转一律 `catchtap`（头像/昵称区）防冒泡；后端 `Post` 无 viewCount 字段，显示浏览数要 `wx:if` 兜住
- 同校/同市：`String(myUser.schoolId) === String(item.schoolId) ? '同校' : '同市'`，任一为空不显示

## 计数 / 关注 / 私信
- 「我的动态」＝帖子+表白墙全部类别，只认后端 `UserVO.dynamicCount`；关注/粉丝用 `t_user.fans_count`/`follow_count` 冗余，同事务 + `greatest(x+delta,0)`
- `t_follow`：`uk_pair(follower_id, followee_id)`；`DuplicateKeyException` 的 catch 必须直接 return（否则计数 +2 漂移）；粉丝数 `select ... for update`
- 私信会话 `user_a=min(id)`/`user_b=max(id)`、`goods_id=0`=非商品；删会话＝只对自己隐藏（`t_message_conversation_hide`，对方发新消息自动取消）；撤回＝软删 `t_message.deleted`，撤回最后一条要回滚会话摘要
- 左滑组件 `components/swipe-cell`：横向占优才接管、`open` 受控、圆角投影放宿主 class、插槽按钮直接 `catchtap`；**聊天页不要快捷话术 chips**（用户明确要求）

## 内容安全（长期有效）
- **任何 UGC 写入口落库前必须过 `SecurityGuard`**：`checkText(userId, text, SCENE_*, BIZ_*)`，带图再 `checkImages(...)`；新增发布入口必须加进 `scripts/content-security-check.js` 的 `ENTRIES`
- 微信调用只允许在 `campus-auth`；业务侧走 Feign `SecurityClient`；文本同步拦（risky → 400 不落库），图片 `media_check_async` 先发后审，结论靠 `/api/wx/callback` 落 `t_media_check`
- 违规事件写 `t_content_audit`：`source=2` 系统自动、`action=REJECT/WARN`、`target_id=0` 表示未落库
- **fail-open**：微信异常一律放行 + warn → 「审核没生效」先查 IP 白名单（40164）与 `WX_SEC_CHECK`/`WX_APPSECRET`
- 回调必须 **JSON + 明文模式**，Token 同 `WX_CALLBACK_TOKEN`（XML/安全模式解析不了）

## campus-admin 后台（8107）
- 网关 `/api/admin/**`（剥 /api + /admin 两层）；直连 8107 不带 X-Admin-* 头
- 独立 JWT：`campus.admin.jwt.secret`（与网关一致，HS256 密钥 ≥32 字节，否则 WeakKeyException），8h
- 跨服务写走 `AdminWriteGateway`：优先 Feign `/internal/admin/**`，失败降级 `FallbackWriteMapper` 直写（大量降级＝服务发现有问题）
- 举报工单 `t_report_ticket` 由 user 与 admin 共享（各持一份实体）；`target_brief` 创建时快照、varchar(500) 必截断；最终 status 由 `handleResult` 推导（req.status 仅兼容保留）
- `t_notification` 正文列名是 **`content_snapshot`**
- MySQL 结果在 Git Bash 带 `\r`，比对前 `tr -d '\r'`；bash 的 `UID` 是只读变量，别拿来存 user id
- 未做（别以为有了）：敏感词库未接 UGC 拦截；广告位/素材、后台公告均无对外接口

## admin-web 前端（Vue3 + Element Plus，长期有效）
- 设计系统单一来源 `src/styles/tokens.scss`（颜色/圆角/阴影/动效）+ `src/styles/index.scss`（Element 全面接管），`main.js` 里 index.scss 必须在 `element-plus/dist/index.css` 之后
- **主色薰衣草紫 `--brand-500 #7C63D8`，渐变 `--brand-gradient`**（取自宣传图「校聚一城」）；侧栏深紫 `--side-bg`；语义色仅 success/warning/danger
- 组件里禁止写死色值，一律 `var(--brand-*)` / `var(--text-*)`；阴影只用 `--shadow-1/2/3`，全部 `rgba(34,30,54,…)`
- ⚠️ **`el-container` 的竖排靠 `direction="vertical"`**：Element 靠 `vnode.type.name === 'ElHeader'` 判断，`Navbar` 是组件包装 → 检测不到就变左右并排（已显式指定）
- ⚠️ **`el-pagination` 的 total 必须是 Number**：后端 `total` 是字符串，Element 用 `typeof v !== 'number'` 判「缺参」→ 整个分页静默不渲染（`PageTable` 已 `Number()` 兜底 + 类型放宽 `[Number, String]`）
- ⚠️ 动态路由注册必须**幂等**（`router.hasRoute(name)`）：登录页已 `loadMenus`，布局里的 `ensureMenus` 看到菜单非空就跳过注册 → 除 `/dashboard` 外全部 404
- 布局：`--side-w 232px` / `--side-w-mini 72px` / `--nav-h 60px`；`Navbar` 面包屑「控制台 › 当前页」；路由切换用 `<transition name="page">` 淡入上移
- 侧栏菜单样式写在**非 scoped** `<style>` + `.sidebar-menu` 命名空间（Element 内部节点拿不到 scoped 属性）；折叠态子菜单 teleport 到 body，需全局补 `.el-menu--popup` 深色底
- 卡片 `.page-card`、表格 `PageTable`（分页 + 自绘空状态）、筛选 `SearchForm`；指标卡 `StatCard` + 数字滚动 `CountUp`
- 表格行动画只用 `opacity`：**别给 `tr` 加 transform**，会破坏 `fixed` 列的 sticky 定位
- 深色登录页：双圆环 + 光斑 + 网格遮罩；卡片入场 `loginIn`，校验失败摇一摇 `shake`；`记住账号` 只存账号不存密码

## 演示数据 / 校验脚本
- `config.js` 的 `DEV_OPENID` 对应 t_user 9001（林小满），别破坏对应关系
- 自测（node 用 `C:/Users/25916/.workbuddy/binaries/node/versions/22.22.2-6/node.exe`）：
  `check-miniprogram.js` 结构 22 页 / `mock-selftest.js` 154 断言 / `home-zones-selftest.js` 35 断言 / `api-smoke-test.js` 后端 45 项（跑完清演示数据）
- 改首页卡片条数时，三套里 `items.length === 6` 的断言必须同步改
- 后台管理员默认 `admin / admin123`（`MD5(CONCAT('admin123','c4m2p9s1'))`）；`vite build` 在沙箱里会因 dist 文件数超阈值失败 → 加 `-- --emptyOutDir=false`

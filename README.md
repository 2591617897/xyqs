# 校园趣事 · 微信小程序

面向同校学生的校园社区：**二手闲置交易** + **匿名表白墙** + **频道式帖子**。
后端 Spring Boot 3 + Spring Cloud Alibaba 微服务，前端为微信小程序（WXML/WXSS/JS）。

> **小程序端默认使用本地模拟数据**（`USE_MOCK = true`），不启动后端也能完整跑通所有页面与交互。
> 详见下文「小程序端：模拟数据模式」。

## 目录结构

```
校园趣事/
├── backend/                     # Spring Cloud 多模块工程
│   ├── pom.xml                  # 聚合父 POM（Spring Boot 3.2.5 / Spring Cloud 2023.0.1）
│   ├── campus-common/           # 统一返回 R、异常、JWT、用户上下文、MyBatis-Plus 配置
│   ├── campus-gateway/   :8888  # 网关：路由 + JWT 鉴权 + 身份头透传
│   ├── campus-auth/      :8101  # 微信/QQ code2Session 登录、JWT 签发、学校字典
│   ├── campus-user/      :8102  # 用户资料、校园身份认证（核心准入）
│   ├── campus-goods/     :8103  # 二手商品、收藏、购买意向
│   ├── campus-wall/      :8104  # 匿名表白墙、评论、点赞
│   ├── campus-post/      :8105  # 频道、帖子、评论、点赞
│   ├── campus-message/   :8106  # 私信会话、聊天记录、消息中心
│   └── sql/                     # schema.sql / data.sql
├── miniprogram/                 # 微信小程序端
│   ├── utils/mock.js            # 本地模拟数据层（假后端，可交互）
│   ├── pages/index              # 发现首页
│   ├── pages/goods/*            # 二手列表 / 详情 / 发布
│   ├── pages/wall/index         # 匿名表白墙
│   ├── pages/post/*            # 频道帖列表 / 详情 / 发帖
│   └── pages/user/*            # 我的 / 校园身份认证
├── scripts/mock-selftest.js     # 模拟数据层自测（96 条断言）
├── docs/architecture.md         # 架构设计与关键取舍
├── docs/api.md                  # 接口文档
└── docker-compose.yml           # MySQL + Redis + Nacos
```

## 技术栈

| 层 | 选型 |
| --- | --- |
| 小程序 | 微信小程序原生（`wx.request` / `wx.login`；`utils/util.js` 保留 QQ 兜底，`PLATFORM` 常量自动识别宿主） |
| 网关 | Spring Cloud Gateway（WebFlux），全局 JWT 过滤器 |
| 注册/配置 | Nacos 2.3（standalone） |
| 服务调用 | OpenFeign + LoadBalancer（`/internal/**` 内部接口） |
| 持久层 | MyBatis-Plus 3.5.7（雪花 ID、分页插件、自动填充） |
| 数据库 | MySQL 8.0 |
| 鉴权 | JWT（HS256，`jjwt 0.12`），网关校验后透传 `X-User-Id / X-School-Id / X-Verify-Status` |

## 小程序端：接口模式

**所有服务器地址集中在 `miniprogram/utils/config.js`，改一处全局生效。**

```js
const ACTIVE_ENV = 'dev'          // 切环境只改这一行

const ENVS = {
  dev:  { scheme: 'http',  host: '127.0.0.1',     port: 8888, mock: false },
  lan:  { scheme: 'http',  host: '192.168.1.100', port: 8888, mock: false },  // 真机预览用局域网 IP
  prod: { scheme: 'https', host: 'api.example.com', port: '',  mock: false }
}
```

由此自动派生，不需要在别处再写地址：

| 变量 | 值 | 用在哪 |
| --- | --- | --- |
| `SERVER` | `http://127.0.0.1:8888` | 图片地址收敛 |
| `BASE_URL` | `SERVER + /api` | 所有接口请求 |
| `UPLOAD_URL` | `SERVER + /api/upload` | 图片上传 |
| `USE_MOCK` | `env.mock` | 是否走本地模拟数据 |
| `TIMEOUT` | 15000 | 请求超时 |

图片地址由 `utils/img.js` 的 `resolve()` 统一收敛：后端返回的 `/uploads/xxx` 或历史旧域名，
都会被改写成当前 `SERVER`——**换域名时历史图片也能跟着切**，不用改数据库。

> 后端侧对应的图片域名是 `campus.upload.base-url`（默认 `http://127.0.0.1:8888`），
> 部署时用环境变量 `UPLOAD_BASE_URL` 覆盖即可。

### 错误处理约定

| 场景 | 表现 |
| --- | --- |
| 401 未登录 / token 过期 | 自动清本地登录态，提示「登录已失效」，页面保留公开内容 |
| 403 未认证 / 无权限 | 提示后端返回的具体原因（如「请先完成校园身份认证」） |
| 404 资源不存在 | 详情页显示「已删除/不见了」+ 返回入口，不弹错误 |
| 5xx / 网络异常 / 超时 | 列表页显示错误态 + 「重新加载」，并**展示后端返回的 msg** |

页面级状态：**加载中**、**空态**（区分「没数据」与「加载失败」）、**提交中**（按钮 loading + 遮罩，防重复提交）。

### 关于 ID：统一用字符串

雪花 ID 是 19 位 Long，**超出 JS 安全整数范围**（`2^53-1` ≈ 9.007e15）。
若以数字返回，小程序 `JSON.parse` 会在末位舍入，表现为「列表有数据，点详情 404」。

后端已在 `campus-common` 的 `JacksonConfig` 里把所有 `Long/long` 序列化为字符串；
前端拿到后**按字符串透传**（拼 URL、写 dataset），不要 `Number()` 转换。
点赞数、评论数等计数是 `Integer`，仍是数字，可正常参与运算。

### 图片上传

发布商品 / 发帖 / 表白 / 评论配图时，选完图片**先上传再提交**：

```
wx.chooseImage → POST /api/upload (multipart) → 拿到绝对 URL → 随业务数据提交
```

后端把文件存到 `campus-user` 的 `./uploads/yyyy/MM/`，并通过 `/uploads/**` 直接对外提供（网关放行，无需 token），返回的 URL 可直接放进 `<image>`。

> 换域名时改 `campus.upload.base-url`（或环境变量 `UPLOAD_BASE_URL`）即可，无需动前端。

### 视觉风格（酷安式信息流）

整体参照酷安的版式与调色做了 1:1 还原并做了美化：

| 元素 | 取值 |
| --- | --- |
| 主色 / 强调色 | `#11a86b`（深一点用 `#0e9a60`，亮一点用 `#17c07c`） |
| 页面背景 | `#f5f6f8`，内容区白底 `#ffffff`，分割线 `#f0f1f3` |
| 价格色 | `#ff4d4f`（保留电商红，与主色区分） |
| 信息流 | `.feed-item` 白底通栏、卡片之间 1rpx 细线分隔（不用阴影） |
| 导航 | 频道/分类横滑 + 选中态绿色下划线指示条 |
| 底部导航 | 5 项自定义 tabBar，中间绿色圆形凸起「＋」发布按钮 |

`app.wxss` 里集中了设计变量与 `.feed-*` 信息流类（作者行 / 正文 / 九宫格 / 热评 / 操作栏），
各页面直接复用，改一处即可全站生效。

### 底部导航栏

使用**自定义 tabBar**（`app.json` 中 `tabBar.custom = true` + `custom-tab-bar/` 组件）：
白底 + 绿色主色，共 5 项，中间为绿色圆形凸起「＋」发布按钮，点击弹出「发闲置 / 写表白 / 发帖」动作面板；
`flex: 1` 均分适配各屏宽，底部用 `env(safe-area-inset-bottom)` 适配全面屏，tab 页内容区同步预留高度避免遮挡。

### 可选：纯前端演示（模拟数据）

把 `USE_MOCK` 改成 `true` 即可（`utils/mock.js` 是一个**可交互的假后端**，不是静态 JSON）：

| 能力 | 说明 |
| --- | --- |
| 接口对齐 | 路径、入参、返回字段与 Spring Boot 接口一致，切换不用改页面 |
| 持久化 | 数据写入小程序 Storage（`mock_db_v1`），退出再进内容还在 |
| 增删改查 | 发布/编辑/下架/删除商品、发表白、发帖、评论、点赞、收藏、购买意向全部生效 |
| 查询能力 | 分页、关键词搜索、分类筛选、价格/热度排序、下拉刷新、上拉加载 |
| 登录与认证 | 模拟登录态；未登录访问受保护接口返回 401，未认证发布返回 403 |

**种子数据**：15 件二手（6 个分类、5 个卖家）、8 条表白墙 + 5 条评论、6 个频道、12 篇帖子 + 5 条评论、2 条交易意向、20 所学校。

**重置数据**：模拟模式下进入「我的」页，**长按底部版本号**恢复初始数据。

**自测**：

```bash
node scripts/mock-selftest.js
# ========== 通过 83 / 失败 0 ==========
```

想体验「未认证 → 提交认证」的完整流程：把 `miniprogram/utils/mock.js` 里 `profile.verifyStatus` 改成 `0`，
再长按底部版本号重置，之后发布内容就会被拦到认证页。

## 快速启动

### 1. 起中间件

```bash
docker compose up -d          # MySQL 3306 / Redis 6379 / Nacos 8848
# 首次启动会自动执行 backend/sql/*.sql，自动创建库 guizhou_campus_fun；若已有数据卷，请手动导入：
# mysql -uroot -p123456 < backend/sql/schema.sql
# mysql -uroot -p123456 < backend/sql/data.sql
```

> 数据库约定：库名 `guizhou_campus_fun`，账号 `root`，密码 `123456`（见 `docker-compose.yml` 与各服务 `application.yml`）。

### 2. 编译并启动后端

需要 **JDK 17**（Spring Boot 3 要求）。

```bash
cd backend
mvn -DskipTests clean install          # 先装 campus-common
# 依次启动（每个开一个终端）
mvn -pl campus-auth    spring-boot:run     # 8101
mvn -pl campus-user    spring-boot:run     # 8102  ← 图片上传 / 静态资源也在这里
mvn -pl campus-goods   spring-boot:run     # 8103
mvn -pl campus-wall    spring-boot:run     # 8104
mvn -pl campus-post    spring-boot:run     # 8105
mvn -pl campus-message spring-boot:run     # 8106
mvn -pl campus-gateway spring-boot:run     # 8888 ← 小程序只访问这个
```

验证：`curl http://127.0.0.1:8888/api/schools` 应返回学校列表。

更完整的自检（逐条打印 23 个核心接口的状态与错误原因，含私信）：

```bash
node scripts/api-smoke-test.js
# ========== 通过 12 / 失败 0 ==========
```

> **常见坑：接口返回 400 "Name for argument of type [...] not specified"**
> 说明后端没有以 `-parameters` 编译。父 POM 已开启该参数，所有 `@RequestParam/@PathVariable`
> 也已显式写死参数名；改完后必须 **先停服务 → `mvn clean install` → 再启动**，
> 增量编译或旧进程都不会生效。

### 3. 跑小程序（连接真实接口）

1. 用 **微信开发者工具** 打开 `miniprogram/` 目录；
2. `project.config.json` 里的 `appid` 换成你自己的（没有可先用测试号）；
3. 详情 → 本地设置 → 勾选 **不校验合法域名**（接口是 `http://127.0.0.1:8888`）；
4. 编译运行，进入「我的」点「一键登录」。

> 上传的图片会落到 `backend/uploads/yyyy/MM/`，通过网关 `http://127.0.0.1:8888/uploads/...` 访问。
> 该目录建议加入 `.gitignore`，生产环境请换成对象存储并修改 `campus.upload.*` 配置。

> 本地无小程序 AppID 时，`campus-auth` 默认开启 mock：
> 用固定的 `DEV_OPENID`（见 `miniprogram/utils/config.js`）生成 openid，
> 保证反复登录都是同一个用户。接真实环境时把 `AUTH_MOCK=false`，
> 并配置 `WX_APPID / WX_APPSECRET`（需要继续支持 QQ 小程序时再填 `QQ_APPID / QQ_APPSECRET`）。

## 微信小程序接入

### 宿主平台识别

`miniprogram/utils/util.js` 统一识别宿主并导出常量，业务代码只用 `mp.*`，不直接判断 `wx` / `qq`：

```js
const IS_QQ = typeof wx === 'undefined' && typeof qq !== 'undefined'
const mp = IS_QQ ? qq : wx
const PLATFORM = IS_QQ ? 'qq' : 'wx'   // 登录时随 code 发给后端
```

### 登录链路支持双平台

`POST /api/auth/login` 的 body 增加 `platform`（`wx` / `qq`，缺省按 `wx`）：

```
wx.login() → code ─┐
                   ├→ POST /api/auth/login { code, platform, devOpenid }
qq.login() → code ─┘        ↓
        AuthService.code2Session(platform, code)
        ├ wx → https://api.weixin.qq.com/sns/jscode2session
        └ qq → https://api.q.qq.com/sns/jscode2session
                   ↓
        Feign → campus-user /internal/users/login-or-create { platform, openid, ... }
```

### 为什么 t_user 必须带 platform

`openid` 只在**宿主平台内**唯一 —— 同一个 QQ 用户的微信 openid 和 QQ openid 是两个毫不相干的字符串，
但反过来，微信和 QQ 的 openid 也**可能**恰好撞上。因此唯一键必须是联合键：

```sql
UNIQUE KEY uk_platform_openid (platform, openid)
```

> 代价要清楚：同一个人分别用微信和 QQ 登录，会产生**两个独立账号**（认证、学号、发帖、点赞全部隔离）。
> 要合并只能靠 unionid（需同一开放平台账号下绑定）或手机号绑定，本项目暂未实现。

迁移脚本（老库必须执行，已在 `backend/sql/schema.sql` 末尾注释）：

```sql
ALTER TABLE t_user ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT 'qq' AFTER id;
ALTER TABLE t_user DROP INDEX uk_openid;
ALTER TABLE t_user ADD UNIQUE KEY uk_platform_openid (platform, openid);
```

> 历史数据的 openid 来自 QQ 小程序，所以默认值必须是 `'qq'` 而不是 `'wx'`，否则老用户会被错标成微信用户。

### 头像与昵称：按微信新规范取

微信已于 2021-04 废弃 `getUserInfo` 的静默授权、2022-11 废弃 `getUserProfile`，
现在**必须由用户主动点击**才能拿到真实头像昵称：

| 能力 | 实现 | 说明 |
| --- | --- | --- |
| 头像 | `<button open-type="chooseAvatar" bindchooseavatar>` | 拿到的是**临时路径**，必须先 `POST /api/upload` 换成可访问 URL 再入库 |
| 昵称 | `<input type="nickname">` | 由微信输入法直接给候选，用户也可自己输入，失焦/确认时 `PUT /api/user/me` |
| 兜底 | 「从相册上传头像」菜单 | 兼容个别基础库上 `chooseAvatar` 不可用的情况 |

> 头像临时路径**不能直接存库**：小程序重启后临时目录会被清理，图片就失效了。

### 真机 / 上线前必做

| 项 | 要求 |
| --- | --- |
| 服务器域名 | 「微信公众平台 → 开发管理 → 服务器域名」配置 `request` / `uploadFile` / `downloadFile` 合法域名 |
| HTTPS | 必须是 HTTPS + 有效证书，`project.config.json` 的 `urlCheck:false` 只在开发者工具生效 |
| 备案 | 域名需已完成 ICP 备案（微信要求） |
| 隐私保护指引 | 在后台声明收集的用户信息（学号、真实姓名、学生证照片），并在页面提供授权说明 |
| 内容安全 | UGC（表白墙、帖子、商品）建议接入 `msgSecCheck` / `imgSecCheck`，否则易被审核驳回 |
| 类目资质 | 「二手交易 / 社区」类目需要对应资质，自营类目审核较严 |

## 核心业务流程

**登录**：`wx.login()` → code → `POST /api/auth/login`（带 `platform`）→ auth 服务按平台
code2Session 换 openid → Feign 调 user 服务 `login-or-create`（按 `(platform, openid)` 定位/建用户）→ 签发 JWT（含 userId / schoolId / verifyStatus）。

**校园身份认证（发布前的硬门槛）**：
`POST /api/user/verify` 提交 `schoolId + 学号 + 真实姓名 + 学生证照片`，
校验学校存在、学号未被占用 → `verify_status=2`（本地默认自动通过，可关掉走人工审核），
同时**重新签发 token**，前端替换后即可发布内容。浏览二手/表白墙不需要认证。

**发布**：商品/帖子/表白均调用 `UserContext.requireVerifiedUserId()`，
未认证返回 403「请先完成校园身份认证」。

**匿名表白墙**：`user_id` 只入库用于风控追溯，接口用 `@JsonIgnore` 屏蔽，
对外展示 `匿名同学·037` 这类由 `user_id + post_id` 派生的昵称——同一人在不同帖子下昵称不同，无法被串联。

## 已实现 / 待补齐

已实现：登录、认证、二手增删改查/搜索/排序/收藏/购买意向、
表白墙（列表 → 详情页浏览 → 点赞 / 评论，评论支持图片 + 表情，本人可编辑/删除帖子、删除自己的评论）、
频道列表、帖子发布/详情/点赞/评论/删除、我的页（资料/统计/意向单）、下拉刷新与上拉加载。

待补齐（接入即可上线）：
1. **图片存储**：当前保存小程序本地临时路径，需接入对象存储（COS/OSS）并换成上传后的 URL；
2. **审核**：`campus.verify.auto-approve=false` 后需要运营审核台；表白墙/帖子建议接内容安全 API；
3. **Redis**：点赞计数、热榜、频控目前在 MySQL 直接累加，可迁移到 Redis；
4. **拆库**：`t_like` 目前被三个服务共用，生产建议按 targetType 拆到各服务库或独立互动服务；
5. **IM**：二手沟通可接腾讯云 IM，替代「联系方式透出」。

## 接口文档

见 [docs/api.md](docs/api.md)。

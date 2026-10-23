# 校园趣事 · QQ 小程序

面向同校学生的校园社区：**二手闲置交易** + **匿名表白墙** + **QQ 频道式帖子**。
后端 Spring Boot 3 + Spring Cloud Alibaba 微服务，前端为 QQ 小程序（WXML/WXSS/JS）。

> **小程序端默认使用本地模拟数据**（`USE_MOCK = true`），不启动后端也能完整跑通所有页面与交互。
> 详见下文「小程序端：模拟数据模式」。

## 目录结构

```
校园趣事/
├── backend/                     # Spring Cloud 多模块工程
│   ├── pom.xml                  # 聚合父 POM（Spring Boot 3.2.5 / Spring Cloud 2023.0.1）
│   ├── campus-common/           # 统一返回 R、异常、JWT、用户上下文、MyBatis-Plus 配置
│   ├── campus-gateway/   :8888  # 网关：路由 + JWT 鉴权 + 身份头透传
│   ├── campus-auth/      :8101  # QQ code2Session 登录、JWT 签发、学校字典
│   ├── campus-user/      :8102  # 用户资料、校园身份认证（核心准入）
│   ├── campus-goods/     :8103  # 二手商品、收藏、购买意向
│   ├── campus-wall/      :8104  # 匿名表白墙、评论、点赞
│   ├── campus-post/      :8105  # 频道、帖子、评论、点赞
│   └── sql/                     # schema.sql / data.sql
├── miniprogram/                 # QQ 小程序端
│   ├── utils/mock.js            # 本地模拟数据层（假后端，可交互）
│   ├── pages/index              # 发现首页
│   ├── pages/goods/*            # 二手列表 / 详情 / 发布
│   ├── pages/wall/index         # 匿名表白墙
│   ├── pages/post/*            # 频道帖列表 / 详情 / 发帖
│   └── pages/user/*            # 我的 / 校园身份认证
├── scripts/mock-selftest.js     # 模拟数据层自测（69 条断言）
├── docs/architecture.md         # 架构设计与关键取舍
├── docs/api.md                  # 接口文档
└── docker-compose.yml           # MySQL + Redis + Nacos
```

## 技术栈

| 层 | 选型 |
| --- | --- |
| 小程序 | QQ 小程序原生（`qq.request` / `qq.login`，兼容 `wx.` 前缀） |
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

### 图片上传

发布商品 / 发帖 / 表白 / 评论配图时，选完图片**先上传再提交**：

```
qq.chooseImage → POST /api/upload (multipart) → 拿到绝对 URL → 随业务数据提交
```

后端把文件存到 `campus-user` 的 `./uploads/yyyy/MM/`，并通过 `/uploads/**` 直接对外提供（网关放行，无需 token），返回的 URL 可直接放进 `<image>`。

> 换域名时改 `campus.upload.base-url`（或环境变量 `UPLOAD_BASE_URL`）即可，无需动前端。

### 底部导航栏

使用**自定义 tabBar**（`app.json` 中 `tabBar.custom = true` + `custom-tab-bar/` 组件）：
浮起式圆角白底、选中态胶囊高亮 + 顶部指示条 + 缩放过渡；`flex: 1` 均分适配各屏宽，
底部用 `env(safe-area-inset-bottom)` 适配全面屏，tab 页内容区同步预留高度避免遮挡。

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
mvn -pl campus-gateway spring-boot:run     # 8888 ← 小程序只访问这个
```

验证：`curl http://127.0.0.1:8888/api/schools` 应返回学校列表。

更完整的自检（逐条打印 12 个核心接口的状态与错误原因）：

```bash
node scripts/api-smoke-test.js
# ========== 通过 12 / 失败 0 ==========
```

> **常见坑：接口返回 400 "Name for argument of type [...] not specified"**
> 说明后端没有以 `-parameters` 编译。父 POM 已开启该参数，所有 `@RequestParam/@PathVariable`
> 也已显式写死参数名；改完后必须 **先停服务 → `mvn clean install` → 再启动**，
> 增量编译或旧进程都不会生效。

### 3. 跑小程序（连接真实接口）

1. 用 **QQ 小程序开发者工具** 打开 `miniprogram/` 目录；
2. `project.config.json` 里的 `appid` 换成你自己的（没有可先用测试号）；
3. 详情 → 本地设置 → 勾选 **不校验合法域名**（接口是 `http://127.0.0.1:8888`）；
4. 编译运行，进入「我的」点「一键登录」。

> 上传的图片会落到 `backend/uploads/yyyy/MM/`，通过网关 `http://127.0.0.1:8888/uploads/...` 访问。
> 该目录建议加入 `.gitignore`，生产环境请换成对象存储并修改 `campus.upload.*` 配置。

> 本地无小程序 AppID 时，`campus-auth` 默认开启 mock：
> 用固定的 `DEV_OPENID`（见 `miniprogram/utils/config.js`）生成 openid，
> 保证反复登录都是同一个用户。接真实环境时把 `QQ_MOCK=false`，
> 并配置 `QQ_APPID / QQ_APPSECRET`。

## 核心业务流程

**登录**：`qq.login()` → code → `POST /api/auth/login` → auth 服务 code2Session 换 openid →
Feign 调 user 服务 `get-or-create` 用户 → 签发 JWT（含 userId / schoolId / verifyStatus）。

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

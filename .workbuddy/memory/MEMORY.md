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
- 校验命令：
  - `node scripts/check-miniprogram.js`（页面结构完整性）
  - `node scripts/mock-selftest.js`（模拟数据层 96 条断言）
  - `node scripts/api-smoke-test.js`（后端 16 个核心接口连通性，**需 Nacos + 服务已启动**）
- 统一响应 `{code,msg,data}`，`code=0` 成功；401 未登录 / 403 未认证 / 404 不存在
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

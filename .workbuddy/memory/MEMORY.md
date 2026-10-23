# 项目长期约定

## 环境参数（本地开发）
- 数据库：`guizhou_campus_fun`，账号 `root`，密码 `123456`
- MySQL `3306` / Redis `6379` / Nacos `8848`
- 网关 `8888`（唯一对外入口，路径前缀 `/api`）
- 服务端口：auth 8101 / user 8102 / goods 8103 / wall 8104 / post 8105
- 修改这些参数时必须同步的位置：
  `docker-compose.yml`、`backend/sql/schema.sql`(CREATE DATABASE)、`backend/sql/data.sql`(USE)、
  5 个 `campus-*/src/main/resources/application.yml`、`miniprogram/utils/config.js`(BASE_URL)、README、docs

## 工程约定
- 小程序端默认 `USE_MOCK = false`（`miniprogram/utils/config.js`，环境化配置：改 `ACTIVE_ENV` 或 `host/port` 即可，`SERVER/BASE_URL/UPLOAD_URL` 自动派生）
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
- **每个页面必须同时产出 4 个文件**：`.js` / `.wxml` / `.json` / `.wxss`；
  `.json` 里显式写 `"usingComponents": {}`——缺 json 会导致整页白屏并报
  `Cannot read properties of undefined (reading "usingComponents")`
- 校验命令：
  - `node scripts/check-miniprogram.js`（页面结构完整性）
  - `node scripts/mock-selftest.js`（模拟数据层 89 条断言）
  - `node scripts/api-smoke-test.js`（后端 16 个核心接口连通性）
- 统一响应 `{code,msg,data}`，`code=0` 成功；401 未登录 / 403 未认证 / 404 不存在
- **ID 一律以字符串传输**：雪花 ID 是 19 位 Long，超出 JS 安全整数（2^53-1）。
  `campus-common` 的 `JacksonConfig` 已把 `Long/long` 全局序列化为字符串；
  前端**不要对 id 做 `Number()`**（会丢精度 → 详情 404）。计数类是 `Integer`，仍是数字。
- 图片：后端存 `./uploads/yyyy/MM/`，返回绝对 URL；前端 `utils/img.js#resolve()` 统一收敛到当前 SERVER

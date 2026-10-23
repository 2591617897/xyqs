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
- 小程序端默认 `USE_MOCK = true`（`miniprogram/utils/config.js`），不依赖后端即可演示
- **每个页面必须同时产出 4 个文件**：`.js` / `.wxml` / `.json` / `.wxss`；
  `.json` 里显式写 `"usingComponents": {}`——缺 json 会导致整页白屏并报
  `Cannot read properties of undefined (reading "usingComponents")`
- 校验命令：
  - `node scripts/check-miniprogram.js`（页面结构完整性）
  - `node scripts/mock-selftest.js`（模拟数据层 69 条断言）
- 改了 `target/` 以外的配置后需 `mvn clean`，否则 `target/classes` 里的旧配置可能被误用
- 统一响应 `{code,msg,data}`，`code=0` 成功；401 未登录 / 403 未认证 / 404 不存在

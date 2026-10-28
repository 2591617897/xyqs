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

## 自测脚本
`check-build-artifacts` / `check-miniprogram` / `content-security` / `security-baseline` / `db-schema-check` / `visibility-selftest` / `demand-selftest` / `promote-selftest` / `mock` / `home-zones`

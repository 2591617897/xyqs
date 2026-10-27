# 项目长期约定（2026-10-27；细节见 daily log / NOTES-admin.md）

## 环境 / 构建
- 库 guizhou_campus_fun/root/123456；Nacos8848、网关8888（唯一入口，前缀 `/api`）
- 端口：auth8101 user8102 goods8103 wall8104 post8105 message8106 admin8107 promote8108
- 线上 `https://campus-api.trkcwl.com`（`ACTIVE_ENV='prod'`）；上域名必须 `UPLOAD_BASE_URL`，上传目录必须绝对路径
- 上传 404：`curl -D -` 见 `200+code:404` = 写 A 读 B（相对路径/多实例）
- `mvn -DskipTests clean install`，子模块只能 `mvn spring-boot:run`；改网关路由**必须重启网关**
- ⚠️ IDE 污染 `target/classes` → 先 `rm -rf target`；「找不到符号 XxxClient」＝common jar 过期

## 通用
- `{code,msg,data}`，0 成功；401/403/404
- **ID 一律字符串**（`JacksonConfig` Long→String），前端禁止 `Number(id)`
- 前端请求不写 `/api`；`Promise.all` 每项各自 `.catch`
- ⚠️ `utils/request.js` 导出**对象** → 只能 `http.get/post/put/del(...)`

## 登录 / 游客
- 只用 `utils/util.js` 的 `mp.*`，禁止判 `wx`/`qq`
- 游客浏览是设计：公开读只放 GET，网关正则必须精确，禁止宽泛放行 `/api/user`
- 不在启动时登录；`ensureLogin()` 无 token 先 `confirmLogin()`，拒绝绝不建号
- **schoolId=null 会让「本校」筛选退化**（＝看全站）→ 有选学校引导；「学籍认证」=verifyStatus==2
- **JWT 的 schoolId/vs 是登录快照** → 改库必须重签（`ensureLogin(true)` + `markTokenFresh()`）

## 安全基线（改前跑 `security-baseline-check.js`，失败须 0）
- 身份只认 JWT；后台要求 token 与 `X-Admin-Id` 一致
- 密钥零硬编码，yml 只写 `${XXX:}`；后台口令 BCrypt(cost12)，admin=`Campus@2026#Temp`；验证**只走网关**
- 加密字段必须 `@TableName(autoResultMap=true)`，漏了＝写入加密读出密文；加密列不能等值 → `SearchHash`
- 备份必须设 `CAMPUS_BACKUP_PASSPHRASE`

## 小程序
- tabBar：`index/zone/messages/user`（`wall/index`、`goods/list` 只能 navigateTo）
- 首页＝功能区布局，**单列全宽**、每区最多 3 张、空区不渲染；入口只来自 `utils/zones.js#buildEntries`
- 品牌=`校聚一城`；闲置只做信息展示**禁止线上交易**（无支付/下单/邮寄）
- 自定义 tabBar 压不住弹层 → 页面 `getTabBar().setData({hidden:true})`

## 布局 / 图片坑
- `scroll-view` 在 flex 列里必须 `min-height:0`；弹层 `height:Xvh` 加 `box-sizing`
- 底部弹层避键盘：`adjust-position="{{false}}"` + `bottom:{{keyboardHeight}}px`
- `.json` 必须显式 `"usingComponents": {}`（缺了整页白屏）
- **引用根目录组件写 `/components/xxx/xxx`（带前导 `/`）**；**主包只放主包用的 js**（否则报冗余）

## 内容安全
- 任何 UGC 落库前必须过 `SecurityGuard#checkText`（带图再 `checkImages`），并加进 `content-security-check.js` 的 `ENTRIES`
- ⚠️ `msg_sec_check`/`media_check_async` 对 chunked POST 回 **412 无 body** → 必须 JDK `HttpClient`+`ofString`，否则被 fail-open 吞成「放行」
- `purify()` **返回 String，必须拿返回值落库**；fail-open 先查 IP 白名单(40164)
- ⚠️ `t_admin_notice.target_type`：1=后台内部通知，绝不能推小程序

## 推广（8108）
- `/api/promote/**` 全部要登录；链路 quota→issue→播广告→complete→(cancel)，单次 100 分、7 天线性衰减
- 防刷 nonce + 最短 15s + 日上限；`PROMOTE_MOCK_AD` 生产必须 false；时间窗口一律 `Duration.toMillis()`
- 同步内容服务失败**必须抛异常回滚**

## 校友群（message 8106）
- **项目无 Redis**：ticket/频控/广播进程内单例 → 多实例必须换 Redis；裁定顺序 **禁言→夜间静音→频率→内容安全**
- **Nginx 必须独立 `location /api/ws`** 且在 `location /` 之前
- 前端 `subpackages/msg/utils/ws.js`：3 次失败降级轮询，重连**必须按 sinceId 补拉**；撤回 `toMillis` 判 2 分钟并广播 `recall` 帧（≠ 管理员 `delete`）
- 长按气泡＝回复/撤回/复制/举报；长按头像＝@；输入栏要包在 `.bottom-dock` 里才能随键盘抬
- @ 面板页内展开（不跳页）：手打 `@` / 长按头像（输入栏 `@` 按钮已移除）；**input 不绑 `cursor` 属性**（会把光标强行挪走）
- **展示名一律用 nickname（「我的」设置的昵称），绝不用 real_name**：UserVO 已移除 realName 字段（仅人工审核/VerifyReq 才碰真实姓名，且不过前端）

## 后台 / 自测
> 后台见 **`NOTES-admin.md`**（网关分层、独立 JWT、el-pagination total 必须 Number）
- `check-miniprogram`（23+12 页）/ `mock` / `home-zones` / `content-security` / `message-notify`（**跑完清演示数据**）
- `db-schema-check.js`：实体↔库直连比对，唯一能拦住「迁移写了没执行」

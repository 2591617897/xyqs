# 项目长期约定（2026-10-27；细节见 daily log / NOTES-admin.md）

## 环境 / 构建
- 库 guizhou_campus_fun/root/123456；Nacos8848、网关8888（唯一入口，前缀 `/api`）
- 端口：auth8101 user8102 goods8103 wall8104 post8105 message8106 admin8107 promote8108 mail8109
- 线上 `https://campus-api.trkcwl.com`（`ACTIVE_ENV='prod'`）；上域名必须 `UPLOAD_BASE_URL`，上传目录必须绝对路径
- 上传 404：`curl -D -` 见 `200+code:404` = 写 A 读 B（相对路径/多实例）
- ⚠️ 上传大小三层会互相掩盖：**admin 的 `spring.servlet.multipart`（默认仅 1MB）→ 业务 `max-size`（5MB）→ 网关 codec 8MB**；报「图片太大」先查 multipart
- admin 的 `campus.admin.upload.dir` 默认 **`../campus-user/uploads`**（与 campus-user 物理同目录），别改回 `./uploads`
- 后台素材上传走 `admin-web/src/components/ImageCropper.vue`（纯 canvas 按规格裁剪 + 压到 1MB），GIF 跳过裁剪直传
- 上传框用**原生 input 铺满**（`inset:0; opacity:0`），不用 `el-upload`：它的点击靠内部 `input.click()`，HMR 后会静默失效（点了没反应且无报错）
- ⚠️ 前端 `catch { ElMessage.error('上传失败') }` 是反模式：请求层已弹后端 msg，会叠成双红条
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
- **学籍认证 `verifyImg` 必须是服务端 URL**（历史 bug：小程序把 `wxfile://tmp_xxx` 本机临时路径直接入库，后台详情显示「加载失败」）；小程序选图一律先 `upload.uploadImage()` 再存返回值，`VerifyReq` 有 `@Pattern("https?://.+")` 兜底，`scripts/check-miniprogram.js` 有 `checkImageUploaded()` 自动拦
- 学籍认证 `verifyStatus=2` 可重认证（`t_user.verify_submit_at` + 7 天冷却 + **强制转人工**，不走 `auto-approve`）；`verifyRemark` 在 `status=1` 是后台备注、`status=3` 才是驳回原因，前端只在 3 时展示
- **账号注销**：**不复用 `status`**（那是封禁），用 `cancelled_at`；昵称落「已注销用户」→ 三个内容服务直查 `t_user` 快照，作者名自动变，**不用改内容服务**；必须清 `verify_status`（否则注销-恢复会重置重认证冷却）；学号留到保留期结束（防换号绕学号唯一）；满期清理把 `openid` 换 `purged:<id>`；恢复必须弹窗让用户确认；`requireUser` 对注销号抛 **403 而非 401**（401 会被前端当掉线清登录态）

## 安全基线（改前跑 `security-baseline-check.js`，失败须 0）
- 身份只认 JWT；后台要求 token 与 `X-Admin-Id` 一致
- 密钥零硬编码，yml 只写 `${XXX:}`；后台口令 BCrypt(cost12)，admin=`Campus@2026#Temp`；验证**只走网关**
- 加密字段必须 `@TableName(autoResultMap=true)`，漏了＝写入加密读出密文；加密列不能等值 → `SearchHash`
- 备份必须设 `CAMPUS_BACKUP_PASSPHRASE`

## 小程序
- tabBar：`index/zone/messages/user`（`wall/index`、`goods/list` 只能 navigateTo）
- 首页＝功能区布局，**单列全宽**、每区最多 3 张、空区不渲染；入口只来自 `utils/zones.js#buildEntries`
- 品牌=`校聚一城`；闲置只做信息展示**禁止线上交易**（无支付/下单/邮寄）
- **首页闲置卡口径（2026-10-27）**：`utils/zones.js#goodsItem` 产出 `sellParts`/`featureMain`/`featureSub`，卡片五层＝标题·描述·卖点行(位置/分类/成色/浏览)·特色条(N人想要|发布时间)·价格行(橙价+划线原价+绿色「看详情」)
- ⚠️ **不抄电商话术**：参考图里的「全网热销20万+件 / 7天无理由退货 / 包邮 / 抢」一律不写 —— 无数据=虚假宣传（审核驳回），「抢」字也违反「闲置仅信息展示」红线；所有槽位只填真实字段
- 自定义 tabBar 压不住弹层 → 页面 `getTabBar().setData({hidden:true})`

## 布局 / 图片坑
- ⚠️ **`<video>` 开了 `controls` 就收不到 `bindtap`**（点击落在原生控件层）→ 想让「点视频全屏」必须把 `controls/show-center-play-btn/show-play-btn/show-fullscreen-btn/show-progress/enable-progress-gesture` 全关，只留首帧当封面，上面盖透明热区 + 自绘播放键/时长；时长用 `bindloadedmetadata` 的 `detail.duration` 自己算
- ⚠️ **关闭键不能压在 video 上**（原生组件会盖住普通 view）→ 全屏层用「顶部条固定高度 + video 从其下方开始」，别用 flex 叠放
- `scroll-view` 在 flex 列里必须 `min-height:0`；弹层 `height:Xvh` 加 `box-sizing`
- 底部弹层避键盘：`adjust-position="{{false}}"` + `bottom:{{keyboardHeight}}px`
- **全面屏底部安全区**：`fixed;bottom:0` 的可点条（输入栏/工具条/操作栏）必须 `padding-bottom: env(safe-area-inset-bottom)`（先写 `0` 兜底），键盘弹起时用 `kb-up` 类归零（键盘已盖住安全区，留着是一截灰缝）；配套占位单独一层 `.input-safe{height:env(...)}`，**不能塞进 rpx 表达式**（env 是环境 px，混 rpx 没法换算）；浮层按钮 bottom 用 `calc(Xrpx + env(...))` 同步抬
- `.json` 必须显式 `"usingComponents": {}`（缺了整页白屏）
- **引用根目录组件写 `/components/xxx/xxx`（带前导 `/`）**；**主包只放主包用的 js**（否则报冗余）
- ⚠️ **WXML 内联 `style` 会静默覆盖 wxss**（「改了没生效」第一反应查它）→ 改布局一律写进 wxss；开发者工具「复制样式」会带 `width/left/top`
- ⚠️ **跳分包必须配 `preloadRule`**，否则首次 navigateTo 白屏等待（如「我的」→`subpackages/msg`）
- ⚠️ **部分 js 是 CRLF 行尾**（`pages/messages/index.js` 等）→ 正则 `/^const x = .*$/m` **匹配不到**（`$` 不匹配 `\r` 前），静默不替换；改文件用 `.replace("精确字符串", ...)` 或先 `cat -A` 看行尾
- **单图口径（2026-10-27 定，全站统一）**：`utils/img.js#imgMode` 统一 `aspectFill`；`imgGridClass(1)=cols-1` → 宽度 100% + CSS **写死 height**（首页 420rpx / 帖子详情 480rpx / 树洞详情 480rpx / 树洞列表 340rpx）。**widthFix 高度=比例×宽，天生无法限高** → 超长竖图会把卡片撑穿；aspectFill 会裁切，想看全图点图走 `mp.previewImage`
- ⚠️ **Python 批量改文件**：写前先判 CRLF/LF（repl 里写死换行符会混进裸 LF，git diff 立刻暴露）；**跨函数的多行正则必须锚定唯一标识**（如 `function resolve(`），否则惰性匹配会吃掉中间整段函数
- `utils/ui.js` 的 `confirm()` 返回 `Promise<boolean>`（`wx.showModal` 封装），二次确认统一用它，别手写 `mp.showModal` 回调

## 内容安全
- 任何 UGC 落库前必须过 `SecurityGuard#checkText`（带图再 `checkImages`），并加进 `content-security-check.js` 的 `ENTRIES`
- ⚠️ `msg_sec_check`/`media_check_async` 对 chunked POST 回 **412 无 body** → 必须 JDK `HttpClient`+`ofString`，否则被 fail-open 吞成「放行」
- `purify()` **返回 String，必须拿返回值落库**；fail-open 先查 IP 白名单(40164)
- ⚠️ `t_admin_notice.target_type`：1=后台内部通知，绝不能推小程序

## 内容可见范围 visibility（2026-10-28）
- **`visibility`（内容可见范围）≠ `scope`（浏览范围）**：scope 是浏览者视角（我以本校/本市身份在逛），visibility 是内容自己的属性（school 仅本校 / city 同市可见）。**禁止把内容字段也叫 scope** —— 那段 OR 嵌套查询会因同名写得极易出错。公共常量 `campus-common/.../content/Visibility`（`normalize` 兜底为 school，不抛 400）
- 过滤规则：浏览本校 `school_id=我的学校`（本校内容无条件可见）；浏览本市 `school_id IN 同城 AND (visibility='city' OR school_id=我的学校)`；无学校身份只看 `visibility='city'`
- ⚠️ **本市流必须透传 `schoolScope`/`viewerSchoolId`**：原 city 分支传 null → 用户连本校同学发的仅本校内容都刷不到
- ⚠️ **只过滤列表不够**：详情接口是独立入口（分享链接可直达），必须单独 `assertVisible()`，抛 **404 而非 403**（不暴露「内容存在但你没权限」）
- ⚠️ **MyBatis Plus 悬空 OR**：`q.eq(A).or().eq(cond, B)` 在 cond 为 false 时留下悬空 `OR` → 整条 SQL 语法错误。必须拆成 if/else 两个分支。`visibility-selftest` 有专项检查
- 历史数据按 `DEFAULT 'school'` 自动回填（收紧），对本校用户零影响

## 推广 / 广告（2026-10-27 广告规格改造）
- **广告尺寸＝规格枚举，禁止自由文本**：唯一清单 `campus-common/.../ad/AdSizeCatalog`（`banner_300/240/200/160`），后台只能选、`t_ad_slot.size_code` 落库，`size_desc` 由它派生（前端传了也不采纳）
- 三端一致链路：后台下拉 `GET /ads/slots/sizes` → 存 `size_code` → `/ads/{code}` 下发 `sizeCode + displayHeight` → 小程序按 `displayHeight` 撑容器（`<ad-banner height>` 默认 0＝跟随规格，**页面不要再传 height**）
- 新增投放位：`AdSlotCatalog#ALL` 加一行（含 defaultSize）+ 小程序接 `<ad-banner code="..." />` + `preloadRule` 视情况，三处缺一就会出现「上架了但不展示」
- 后台素材页按规格校验素材比例（>12% 偏差提醒，不拦截）

## 邮件服务（campus-mail 8109 + RabbitMQ）
- **业务服务不碰 SMTP、不拼邮件 HTML**：只投 `MailMessage{requestId,to,template,params,bizType,bizId}` 到 `campus.mail` / `mail.send`；正文与凭据全归 mail 服务
- 契约在 `campus-common/mail/`（`MailQueues` 队列名 + `MailTemplates` 模板编码 + `MailMessage`），**禁止各服务自己写字符串**
- 消费失败必须**向上抛**（AMQP 重试 3 次 → `campus.mail.dlq`）；catch 掉＝MQ 认为成功＝邮件静默丢失
- 幂等靠 `t_mail_log.request_id` 唯一索引；`status` 1 成功 / 2 失败；`GET /internal/mail/logs/{requestId}` 查结果
- ⚠️ **队列拓扑生产端和消费端各声明一份**（参数逐字一致，含 DLX），只让消费端声明会导致「生产端先启动→消息发到不存在的 exchange→静默消失」
- ⚠️ **`scanBasePackages` 不能写 `"com.campus"`**：会扫到 common 的 `SecurityGuard`，它构造依赖 Feign 的 `SecurityClient` → 服务起不来。只挑 `com.campus.common.{config,exception,mybatis}`
- ⚠️ **网关放行 ≠ 服务内放行**：`AdminSecurityConfig#excludePathPatterns` 必须同步加路径，否则网关放过去了、admin 拦截器又 401
- 本机 RabbitMQ 是安装版 guest/guest（15672 管理台）；`docker-compose.yml` 里的容器用 campus/campus123（guest 只允许容器内 localhost）

## 后台找回密码（邮箱验证码）
- `/auth/find-password/{code,reset}` 两条免登录；**收件邮箱只取 `t_admin_user.email`，前端不能传**（否则＝任意邮箱收码 + 任意账号改密）
- 验证码 6 位、BCrypt 存 `t_admin_pwd_reset`、10 分钟；限流 60s 冷却 / 单账号 24h 5 次 / 单 IP 1h 20 次 / 试错 5 次
- 重置成功**顺手解锁**：`fail_count=0` + `locked_until=NULL` + `token_version+1` + `must_change_pwd=0`（被锁 15 分钟的人正是最需要这条通道的）
- 账号必须先在「系统管理→管理员→编辑→邮箱」绑邮箱，否则提示去联系超管（当前 admin 账号 email 为 NULL）

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
- **群名固定「校友群」，不拼学校名**（`GroupJoinService.GROUP_NAME`；学校身份只走群设置页副标题 `GroupDetailVO.schoolName`）——长校名会把顶栏截成「贵州铜仁数据职业学院...」；建群名逻辑只在「新学校首个用户入群」时跑，改了必须**同时**执行 `migration-20261027-group-name.sql` 修历史数据，否则新旧群名并存

## 后台 / 自测
> 后台见 **`NOTES-admin.md`**（网关分层、独立 JWT、el-pagination total 必须 Number）
- `check-miniprogram`（23+12 页）/ `mock` / `home-zones` / `content-security` / `message-notify`（**跑完清演示数据**）
- `db-schema-check.js`：实体↔库直连比对，唯一能拦住「迁移写了没执行」
- ⚠️ **「留空表示不修改」的字段禁止用 `@Size`**：`@Size` 只放过 `null`，空串 `""` 会被判长度不足 → 400「密码长度需在 8-64 之间」，请求在 Service 之前就被拦掉（`AdminUserReq.password` 已改 `@Pattern(regexp="^$|^[\\s\\S]{8,64}$")`）。前端配套：提交前 `delete payload.password`，编辑态 validator 留空直接放行

## 需求广场 demand（2026-10-28 立模块；细节见 daily log）
- 两类 `1求物 / 2求帮忙`；**红线**：只做信息展示 —— 不接支付、不做担保、**不做评价打分**（有评分=服务平台，类目资质不同级）；`budget` null = 面议
- **独立表 `t_demand` + `t_demand_reply`**，不给 `t_goods` 加 flow（字段/状态机全反的，混存后漏一处过滤需求就混进卖货列表）
- 状态：1 进行中（列表只查它）/ 2 已完成（已找到）/ 0 已下架，三者可互转
- ⚠️ **两套分类 id 各自从 1 开始**（求物 7 / 求帮忙 6）→ 按 `category_id` 查询**必须带 type**；前端换大类清 categoryId
- 回应方向与 `t_goods_order` 相反（有货的人 → 发布者）；**一人一需求只能一条**；只有发布者能看回应
- 接口路径 `/demands/replies/mine` 刻意带 `mine` 段（网关按段判私域，自定义段名会被 PUBLIC_READ 放行）；`PRIVATE_SEGMENTS` 已加 `replies`
- 分包 `subpackages/demand/{list,detail,publish,mine}` + `utils/demand.js`；preloadRule 挂 zone + user + goods/detail
- ⚠️ 上线**必须重启网关**（`/api/demands/**` 新路由），否则整模块 404
- 后台：菜单 9330 段；`/admin/content/demands*`；写走 Feign `/internal/admin/demands/**`；
  **删除不做降级直写**（不可逆）；分类名由 goods 下发，admin 不抄一份
- 互通推荐 `DemandMatchService`：`/demands/{id}/matches` + `/goods/{id}/demands`；
  标题拆词匹配（标题+2/描述+1）、候选池 300 条内存打分、最多 3 条；推荐也要过可见范围
- 自检 `scripts/demand-selftest.js`（100）

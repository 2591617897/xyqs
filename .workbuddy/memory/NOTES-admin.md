# admin 后台细节备忘（campus-admin 8107 + admin-web Vue3）

> 从 MEMORY.md 拆出，避免主文件过长。做后台相关任务时读本文件。

## campus-admin（8107）
- 网关 `/api/admin/**`（剥 /api + /admin 两层）；直连 8107 不带 X-Admin-* 头
- 独立 JWT `campus.admin.jwt.secret`（与网关一致，HS256 ≥32 字节），8h
- 跨服务写走 `AdminWriteGateway`：优先 Feign `/internal/admin/**`，失败降级 `FallbackWriteMapper`（大量降级=服务发现问题）
- 举报工单 `t_report_ticket` user 与 admin **各持一份实体**；`target_brief` varchar(500) 必截断；status 由 `handleResult` 推导
- `t_notification` 正文列名是 **`content_snapshot`**
- MySQL 结果在 Git Bash 带 `\r`，比对前 `tr -d '\r'`；bash 的 `UID` 只读，别拿来存 user id
- 管理员默认 `admin / admin123`（`MD5(CONCAT('admin123','c4m2p9s1'))`）
- `vite build` 沙箱里 dist 文件数会超阈值 → 加 `-- --emptyOutDir=false`

## admin-web（Vue3 + Element Plus）
- 设计系统 `src/styles/tokens.scss` + `index.scss`；`main.js` 里 index.scss 必须在 `element-plus/dist/index.css` 之后
- 主色薰衣草紫 `--brand-500 #7C63D8`（渐变 `--brand-gradient`）；侧栏深紫 `--side-bg`
- 组件里禁止写死色值，一律 `var(--brand-*)`/`var(--text-*)`；阴影只用 `--shadow-1/2/3`
- ⚠️ `el-container` 竖排靠 `direction="vertical"`（Element 按 `vnode.type.name === 'ElHeader'` 判断，`Navbar` 是组件包装 → 检测不到就变左右并排）
- ⚠️ `el-pagination` 的 total 必须 **Number**（后端给字符串，Element 判「缺参」→ 分页静默不渲染；`PageTable` 已 `Number()` 兜底）
- ⚠️ 动态路由注册必须**幂等**（`router.hasRoute(name)`）：登录页已 `loadMenus`，布局 `ensureMenus` 见菜单非空就跳过 → 除 `/dashboard` 外全 404
- ⚠️ **入场动画 fill-mode 用 `backwards` 不用 `both`**（2026-10-26 排查的弹窗裁剪根因）：`both` 结束后 Chrome 残留恒等 `transform: matrix(1,0,0,1,0,0)`，让 `.page-card` 成为 `position:fixed` 的包含块 → 默认 in-place 渲染的 `el-dialog` 遮罩被压成卡片大小、再被 `.app-main`(overflow:auto) 裁掉，弹窗「只显示上半截、保存按钮看不见」。已全局改 `backwards`（index.scss `.page-card`/`.fade-in`/`rowIn`、PageTable popIn、StatCard statIn、dashboard cardIn、403/404）
- ⚠️ **弹窗全局限高**（index.scss）：`.el-dialog` flex 列 + `max-height: calc(85vh - 50px)` + `__body` overflow-y:auto → 矮窗口/浏览器缩放下头部与「保存」按钮恒可见，只有内容滚
- ⚠️ **刷新/直开动态页 404**：顶层 NotFound 在 Layout 外，`ensureMenus` 没机会跑 → `router/index.js` 全局 `beforeEach` 守卫补注册（`retriedPaths` 每路径只试一次防死循环），然后重定向重解析
- ⚠️ **后端加新接口必须重启对应服务**：RegionController 的 `GET /areas`/`GET /schools` 加了没重启 8107 → 网关透传后 405「Request method 'GET' is not supported」（前端表现为 toast「服务器开小差了」）；`mvn install` 重编后重启即好
- 布局 `--side-w 232px` / `--side-w-mini 72px` / `--nav-h 60px`；面包屑「控制台 › 当前页」；路由切换 `<transition name="page">` 淡入上移
- 侧栏菜单样式写在**非 scoped** `<style>` + `.sidebar-menu` 命名空间（Element 内部节点拿不到 scoped 属性）；折叠态子菜单 teleport 到 body，需全局补 `.el-menu--popup` 深色底
- 卡片 `.page-card`、表格 `PageTable`（分页 + 自绘空态）、筛选 `SearchForm`、指标卡 `StatCard` + `CountUp`
- 表格行动画只用 `opacity`：**别给 `tr` 加 transform**（破坏 fixed 列 sticky）
- 深色登录页：双圆环 + 光斑 + 网格遮罩；卡片入场 `loginIn`，校验失败 `shake`；「记住账号」只存账号不存密码

## 运营位实现要点

- 广告/公告 campus-post 侧用 `AdMaterialVO`/`NoticeVO` 直接接收（map-underscore-to-camel-case，**不建实体副本**）
- 四道过滤缺一不可：上架 + 过审 + 投放时间窗 + 地域定向
- ⚠️ `PUBLIC_READ_PREFIX` **只放行 GET** —— 任何 POST（如 `/api/ads/materials/{id}/click`）必须另加正则精确放行
- ⚠️ `t_admin_notice.target_type` 是**安全边界**：1=后台管理员内部通知，绝不能推给小程序，只放行 2/3
- `t_admin_notice` 字段只有 type/target_type/level（**没有** cover/link_type/is_top/scope_type）
- 敏感词三级：1 WARN 不改 / 2 按 `replace_to` 替换（空则 `***`）/ 3 抛 400 不落库；`selectEnabled()` 必须 `ORDER BY CHAR_LENGTH(word) DESC`
- 演示数据 `backend/sql/data-operate-demo.sql`

## 广告位编码契约（2026-10-26 补，改这块前必读）
- **code 是后台与小程序唯一的契约**，拼错一个字符小程序就一条拉不到且两边都不报错
  → 已收敛成受控枚举 `campus-admin/config/AdSlotCatalog`（`AdSlotOption`），前端只能下拉选
- ⚠️ **position 由 code 推导，不接受前端传值**（createSlot 覆盖、updateSlot 按 code 重设）。
  以前 position 是独立表单字段，投放查询却只认 code → position 纯装饰，能造出
  「位置=表白墙、code=home_banner」的矛盾数据
- 6 个 code：`home_banner`(1) / `zone_top`(2) / `wall_top`(3) / `goods_top`(4) /
  `splash`(5 未接入) / `mine_banner`(6)。**顺序不能动**（position 已进历史数据）
- 每个 code 只能建 1 个广告位，多条素材建在它下面；`connected=false` 的位**上架一律 400**
  （杜绝「上架成功但没人看到」）
- 小程序侧统一组件 `components/ad-banner/ad-banner`（properties: `code` / `height` rpx），
  自己读 `globalData.user.schoolCity` 做城市定向、自己上报点击；素材空时整块不进布局。
  「我的闲置/收藏」是个人列表，`goods_top` 用 `wx:if="{{!mineMode && !favMode}}"` 排除
- 跳转分支收敛到 `utils/ad.js`（`reportClick` / `open`），首页 `onAdTap` 也改用它；
  linkType：0 无 / 1 帖子 / 2 商品 / 3 外链(复制剪贴板) / 4 小程序页面路径
- 列表页会打「未对应」标（编码不在注册表）+ 顶部汇总告警，历史脏数据靠它兜住

## 组织架构表单（/system/regions，2026-10-26 改）
- **省市必须下拉，不许手输**：`GET /admin/regions/areas` 返回 `[{province, cities[]}]`，
  数据由 `t_school` 的 province/city **去重**得到（`RegionDictMapper#selectProvinceCity`）
- 为什么不能用行政区域表/手输：`t_school` 存的是 **「贵州 / 贵阳」不带「省 / 市」后缀**，
  手输「贵州省」→ 辖区反查 0 所学校 → 节点能建、树能看，负责人数据权限却是空的
- 校区也改下拉：`GET /admin/regions/schools?province=&city=&keyword=`（`SchoolOption`），
  替代手填 19 位雪花 `t_school.id`
- 层级不再手选，由选择自动推导：`type=2 → 3`；`type=1 && city → 2`；`type=1 && !city → 1`
- ⚠️ **辖区展开必须「填了才参与匹配」**（已修正）：旧 SQL 是
  `t_school.city = g2.city AND t_school.province = g2.province` 硬等值，省节点 city 为空 →
  永远匹配 0 所。现为 `(g2.province IS NULL OR g2.province='' OR t_school.province=g2.province) AND (...)`
  - 实测：贵州（city 空）→ 46 所；旧版 → 0；贵州+贵阳 → 24 所
- 前端 `admin-web/src/views/system/RegionTree.vue`：省 / 市 / 校区三个 el-select（filterable），
  换省清空非法城市、选校区自动回填省市并向空名称填学校名、`buildPayload()` 提交前归一 level

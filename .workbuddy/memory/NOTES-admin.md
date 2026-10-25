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

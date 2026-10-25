# 后台管理（campus-admin + admin-web）

面向运营与校区负责人的**独立后台**：内容审核、用户处置、互动查询、广告位投放、公告、敏感词、数据看板。
技术栈沿用主工程（Spring Boot 3.2.5 / Spring Cloud Alibaba / MyBatis-Plus / MySQL），
前端 Vue3 + Element Plus + Vite，统一走网关 `/api/admin/**`。

## 一、边界与定位

| 维度 | 结论 | 理由 |
| --- | --- | --- |
| 管理员体系 | **独立**：`t_admin_user` + 独立 JWT + `/api/admin/auth/login` | 后台账号和小程序用户是两类人，混用一套 token 会让权限模型失控 |
| 内容状态 | **不复制**：帖子/墙/商品的可见性仍由各自服务的 `status` 持有 | 后台只记处置流水 `t_content_audit`，避免两边状态漂移 |
| 跨服务写 | Feign 调各服务新增的 `/internal/**`，失败降级为 admin 同库直写 | 写归属方自己的库；服务挂了后台仍能处置，不至于完全不可用 |
| 入口 | 网关 `StripPrefix=2`，`AuthGlobalFilter` 有 admin 独立分支 | 与 C 端共用 8888，不额外暴露端口 |

### 匿名内容的「前台匿名 + 后台可追溯」

表白墙匿名内容在 C 端只暴露 `anon_name` / `anon_avatar`；后台详情接口 `GET /content/wall/{id}`
会额外返回 `realUserId` / `realNickname`，并在处置时把真实作者写进 `t_content_audit.author_id`。
**这两个字段只在后台链路出现，任何面向小程序的接口都不返回。**

## 二、鉴权链路

```
admin-web  →  /api/admin/auth/login         网关放行（白名单）
           →  campus-admin 签发 JWT（独立密钥，claim typ=admin）

后续请求  →  网关 AuthGlobalFilter（order=-100）
               ├ 路径以 /api/admin 开头 → 用 admin 密钥校验，要求 typ=admin
               ├ 校验通过 → 注入 X-Admin-Id / X-Admin-Name / X-Admin-Super / X-Admin-Scope
               └ 校验失败 → 401「凭证类型不正确」（C 端 token 进不来）

           →  campus-admin AdminInterceptor 还原 AdminContext
           →  PermissionInterceptor 读 @RequirePermission 做接口级校验（60s 缓存）
           →  Service 层 DataScopeResolver 按数据范围过滤 school_id
```

三层各管一段：**网关管身份、管类型；拦截器管权限点；Service 管数据范围**。

## 三、数据权限（DataScope）

| dataScope | 含义 | 行为 |
| --- | --- | --- |
| 1 | 全部 | 不加学校过滤 |
| 2 | 指定校区 | 只看到 `t_admin_region_leader` 里挂给自己的校区 |
| 3 | 仅本人数据 | 只看到自己创建/处置过的 |

超管（`is_super=1`）直接放行。**过滤后的学校集合为空 = 看不到任何数据**（不是「看全站」）——
宁可少给，不能漏给。`RegionService#assignLeader` 把某人设为校区负责人时会把 `dataScope` 切成 2。

## 四、跨服务写与降级

`AdminWriteGateway` 是唯一出口，每个动作先 Feign、失败 `log.warn` 后走 `FallbackWriteMapper` 同库直写：

| 动作 | 正常路径（Feign） | 降级路径（直写 SQL） |
| --- | --- | --- |
| 帖子隐藏/恢复 | `POST /internal/admin/posts/{id}/status` | `UPDATE t_post SET status=?` |
| 墙帖隐藏/恢复 | `POST /internal/admin/wall/{id}/status` | `UPDATE t_wall_post SET status=?` |
| 商品下架/恢复 | `POST /internal/admin/goods/{id}/status` | `UPDATE t_goods SET status=?`（带 `AND status <> 2`，已售出不让改回） |
| 删评论 | `DELETE /internal/admin/{post,wall,goods}/comments/{id}` | 物理删除 + 回写 `comment_count` |
| 封禁/解封 | `POST /internal/admin/users/{id}/status` | `UPDATE t_user SET status=?` |
| 禁言投影 | `POST /internal/admin/users/{id}/mute` | `UPDATE t_user SET mute_until=?` |
| 实名审核 | `POST /internal/admin/users/{id}/verify` | `UPDATE t_user SET verify_status=?, verify_remark=?` |
| 删私信 | `POST /internal/admin/messages/{id}/delete` | `UPDATE t_message SET deleted=1` |

降级路径刻意补齐了正常路径会做的副作用（比如删评论要回写 comment_count），
保证「Feign 通」和「Feign 不通」两种情况下的**最终结果一致**。

## 五、数据库表

P0 骨架（`migration-20261025-campus-admin.sql`）：

| 表 | 作用 |
| --- | --- |
| `t_admin_user` | 管理员、密码加盐 MD5、锁定策略 |
| `t_admin_role` / `t_admin_menu` / `t_admin_user_role` / `t_admin_role_menu` | RBAC，菜单到按钮级 |
| `t_admin_operation_log` / `t_admin_login_log` | 审计日志（有界队列异步落库） |
| `t_admin_region` / `t_admin_region_leader` | 校区/地区树与负责人 |

P1 业务（`migration-20261025-admin-business.sql`）：

| 表 | 作用 |
| --- | --- |
| `t_content_audit` | 处置流水，含匿名内容的 `author_id` 快照 |
| `t_report_ticket` | 举报工单（与 C 端共用一张表，后台只受理不创建） |
| `t_sensitive_word` | 敏感词，level 1记录 / 2替换 / 3禁止 |
| `t_user_mute` | 禁言，**事实源** |
| `t_user_blacklist` | 黑名单，同结构 |
| `t_ad_slot` / `t_ad_material` | 广告位与素材，审核状态与上下架状态双闸 |
| `t_admin_notice` | 通知公告 |
| `t_dashboard_daily` | 看板日表（物化，可 rebuild） |
| `t_user_verify_log` | 实名审核流水 |

### 两个容易踩的状态约定

- **禁言 / 黑名单的「已解除」写成 `status = NULL`，不是 0。**
  MySQL 唯一索引允许多个 NULL，这样 `uk_user_scope_active(user_id, scope, status)`
  既能保证「同一用户同一范围最多一条生效记录」，又允许多条历史解除记录共存。
  写 NULL 必须走 `lambdaUpdate().set(...)`，**`updateById` 会跳过 null 字段**。
- **`t_user.mute_until` 只是投影列**，事实源是 `t_user_mute`，两者在同一事务里同步；
  C 端判断只读投影列，后台改事实源后立刻刷投影。

## 六、接口清单

前缀统一 `/api/admin`，下面省去。

### 认证与组织

| 方法 | 路径 | 权限点 |
| --- | --- | --- |
| POST | `/auth/login` `/auth/logout` | 🔓 |
| GET | `/auth/info` `/auth/menus` | 登录即可 |
| GET/POST/PUT/DELETE | `/admins` `/admins/{id}` | `admin:admin:*` |
| GET/POST/PUT/DELETE | `/roles` `/roles/{id}` `/roles/{id}/grant` | `admin:role:*` |
| GET/POST/PUT/DELETE | `/menus` `/menus/{id}` | `admin:menu:*` |
| GET | `/regions/tree` `/regions/leaders` | `admin:region:list` / `admin:leader:list` |
| POST | `/regions/leaders/assign` `/regions/leaders/{id}/revoke` | `admin:leader:*` |

### 内容管理

| 方法 | 路径 | 权限点 |
| --- | --- | --- |
| GET | `/content/posts` `/content/posts/{id}` | `admin:content:post:list` / `:detail` |
| POST | `/content/posts/{id}/status` | `admin:content:post:hide` |
| GET | `/content/wall` `/content/wall/{id}` | `admin:content:wall:list` / `:detail` |
| POST | `/content/wall/{id}/status` | `admin:content:wall:hide` |
| GET | `/content/goods` `/content/goods/{id}` | `admin:content:goods:list` / `:detail` |
| POST | `/content/goods/{id}/status` | `admin:content:goods:off-shelf` |
| GET | `/content/comments` | `admin:content:comment:list` |
| POST | `/content/comments/{id}/delete?targetType=&targetId=` | `admin:content:comment:delete` |
| GET | `/content/audits` | `admin:content:audit:list` |

### 用户 / 工单 / 互动

| 方法 | 路径 | 权限点 |
| --- | --- | --- |
| GET | `/users` `/users/{id}` `/users/{id}/stats` | `admin:user:list` / `:detail` |
| POST | `/users/{id}/ban` `/unban` | `admin:user:ban` |
| GET | `/users/blacklist` | `admin:user:blacklist:list` |
| POST | `/users/{id}/blacklist` `/blacklist/release` | `admin:user:blacklist` |
| POST | `/users/{id}/mute` `/unmute` `/mutes/release-expired` | `admin:user:mute` |
| GET | `/users/mutes` | `admin:user:mute:list` |
| GET | `/users/verify/pending` `/users/verify/logs` | `admin:user:verify:list` |
| POST | `/users/{id}/verify` | `admin:user:verify` |
| GET | `/reports` `/reports/{id}` | `admin:report:list` / `:detail` |
| POST | `/reports/{id}/handle` | `admin:report:handle` |
| GET | `/chat/conversations` `/chat/messages` | `admin:chat:conversation:list` / `admin:chat:message:list` |
| POST | `/chat/messages/{id}/delete` | `admin:chat:message:delete` |

### 运营与看板

| 方法 | 路径 | 权限点 |
| --- | --- | --- |
| GET | `/ads/slots` `/ads/materials` | `admin:ad:*:list` |
| POST/PUT/DELETE | `/ads/slots[/{id}]` `/ads/materials[/{id}]` | `admin:ad:slot:*` / `admin:ad:material:*` |
| POST | `/ads/slots/{id}/audit` `/ads/slots/{id}/status` | `admin:ad:slot:update` / `:status` |
| POST | `/ads/materials/{id}/audit` `/ads/materials/{id}/status` | `admin:ad:material:audit` / `:online` |
| GET/POST/PUT/DELETE | `/notices` `/notices/{id}` | `admin:notice:*` |
| POST | `/notices/{id}/publish` `/offline` | `admin:notice:publish` |
| GET | `/sensitive` `/sensitive/check?text=` | `admin:sensitive:list` |
| POST/PUT/DELETE | `/sensitive[/{id}]` `/sensitive/reload` | `admin:sensitive:*` |
| GET | `/dashboard/overview` `/dashboard/daily` | `admin:dashboard:view` |
| POST | `/dashboard/rebuild?date=` | `admin:dashboard:rebuild` |
| GET | `/logs/operations` `/logs/logins` | `admin:log:*:list` |

> 广告是**两道独立闸**：审核闸（`audit_status`）和上下架闸（`status`），未过审不许上架；
> 素材还额外要求所属广告位已上架。冒烟脚本对这四个组合各有一条断言。

## 七、前端页面

`admin-web/src/views/`：

- `dashboard/index.vue` —— 概览卡片 + 日表
- `content/` —— PostList / WallList / GoodsList / CommentList / AuditList
- `user/` —— UserList / VerifyList / Blacklist / MuteList
- `report/TicketList`、`chat/` —— ConversationList / MessageList
- `ad/` —— SlotList / MaterialList；`notice/NoticeList`；`sensitive/WordList`
- `system/` —— AdminList / RoleList / MenuList / RegionTree / LeaderList

路由用 `import.meta.glob` 自动挂载，按钮权限用 `v-permission` 指令。

## 八、自检

```bash
node scripts/admin-smoke-test.js [http://127.0.0.1:8888] [admin] [admin123]
# ========== 通过 56 / 失败 0 ==========
```

七段：网关鉴权边界 → 登录（含错误密码/锁定）→ 登录后基础接口 → 接口级权限校验
→ P1 只读接口 → **处置联动端到端** → 操作日志落库。

第 [6] 段会真实改动库里第一条帖子和第一个用户的状态，跑完立即还原。

## 九、已踩过的坑

| 症状 | 根因 | 处理 |
| --- | --- | --- |
| `You have an error in your SQL syntax ... near '; NOW())), 1, 0) muted` | `@Select` **没套 `<script>`**，MyBatis 只对带 script 的注解做 XML 实体解析，`&gt;` 原样进了 SQL | 该注解补上 `<script>`；两条 select 保持写法一致 |
| 解除禁言后 `mute_until` 清不掉 | `updateById` 的 NOT_NULL 策略跳过 null 字段 | 改 `lambdaUpdate().set(User::getMuteUntil, null)`；`verify` 通过时清 remark 同理 |
| `Ambiguous mapping. Cannot map ... GET [/reports]` | P0 遗留的 `ReportAdminController` 与 P1 的 `ReportController` 撞路由 | 删掉旧的，保留带联动处置与防重复受理的新版本 |
| 登录报「账号或密码错误」 | 库里密码与种子 `MD5(CONCAT('admin123','c4m2p9s1'))` 不一致 | 重跑种子里的 UPDATE 语句 |

# 接口文档

- 统一入口：`http://127.0.0.1:8888/api`（网关端口 8888）
- 鉴权：`Authorization: Bearer <token>`；标注 🔓 的接口未登录也可访问
- 统一响应：`{ "code": 0, "msg": "ok", "data": ... }`，`code != 0` 即失败（401 未登录 / 403 未认证 / 404 不存在）
- 分页响应：`{ records: [], total, page, size, hasMore }`

> **ID 一律是字符串**。雪花 ID 为 19 位 Long，超出 JavaScript 安全整数范围（2^53-1），
> 后端已全局把 `Long/long` 序列化为字符串，前端请按字符串透传，**不要 `Number()` 转换**，否则会丢精度导致查不到数据。
> 计数类字段（点赞数、评论数、浏览量）是 `Integer`，仍为数字。

## 认证 / 学校（campus-auth）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 🔓 登录。body：`{code, platform?, nickname?, avatar?, devOpenid?}`，`platform` 取 `wx`（默认）/ `qq`，决定用哪套 appid 去换 openid。返回 `{token, user:{id,nickname,avatar,schoolId,verifyStatus}}` |
| GET | `/schools?keyword=` | 🔓 学校字典（注册/认证必选），最多 50 条 |

## 用户（campus-user）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/user/me` | 我的资料（学号、姓名已打码，含 schoolName / verifyStatus / verifyRemark，新增 `fansCount / followCount` 计数字段，供「我的」页展示） |
| PUT | `/user/me` | 修改昵称、头像、性别、手机、简介 |
| POST | `/user/verify` | 提交校园认证。body：`{schoolId, studentNo, realName, verifyImg}` → `{verifyStatus, token, message}`（token 需替换本地） |
| GET | `/user/{userId}/profile` | 🔓 他人公开主页资料与统计（见下方「关注 / 粉丝与个人主页」）。被封禁/不存在返回 404「用户不存在或已被封禁」 |
| POST | `/user/follow/{userId}` | 关注（需登录）。幂等：已关注直接成功不重复计数。返回 `{following:true, fansCount}` |
| DELETE | `/user/follow/{userId}` | 取消关注（需登录）。未关注也返回成功。返回 `{following:false, fansCount}` |
| GET | `/user/follow/status?userId=` | 当前用户对目标的关注状态，`data` 为布尔值（true=已关注；未登录返回 `false`，不报 401） |

### 关注 / 粉丝与个人主页

关注错误码：关注自己 400「不能关注自己」；目标不存在/被封禁（status=0）404「对方不存在或已被封禁」；未登录调 POST/DELETE 401。

`GET /user/{userId}/profile` 响应 `data`（ProfileVO，不含 phone / studentNo / realName / verifyImg 等敏感字段）：

```json
{
  "id": "9002",                 // 字符串（雪花 ID）
  "nickname": "陈屿",
  "avatar": "http://127.0.0.1:8888/uploads/...",
  "bio": "计算机学院，出闲置回消息很快",
  "schoolId": "1005",           // 字符串
  "schoolName": "浙江大学",
  "verifyStatus": 2,            // 0未认证 1审核中 2已认证 3驳回
  "fansCount": 12,              // 粉丝数（数字）
  "followCount": 5,             // 关注数（数字）
  "following": false,           // 当前登录用户是否已关注；未登录/本人 = false
  "isSelf": false,              // 是否本人主页
  "goodsSaleCount": 2,          // 闲置在售数（内部接口聚合）
  "goodsTotalCount": 4,         // 闲置累计发布数（内部接口聚合）
  "postCount": 3                // 帖子数（内部接口聚合）
}
```

前端徽章规则：`verifyStatus === 2` → 「✓ 已认证」，否则「未认证」。

## 二手商品（campus-goods）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/goods` | 🔓 列表。参数：`keyword, categoryId, sort(latest/price_asc/price_desc/hot), page, size, schoolId, userId`。**传 `userId` 时返回该作者全部状态商品**（0下架/1在售/2已售出，前端渲染状态标签），忽略学校范围与默认 `status=1` 过滤；不传时行为不变 |
| GET | `/goods/categories` | 🔓 分类：数码/图书/生活/服饰/运动/其他 |
| GET | `/goods/{id}` | 🔓 详情（自动 +1 浏览量，登录时返回 `liked`；含规格字段 `conditionLevel/purchaseTime/usageFrequency/source/warranty/supportTrial` 与卖家统计 `sellerSaleCount/sellerTotalCount`） |
| POST | `/goods` | 发布（需认证）。body：`{title, description, price, originalPrice, categoryId, conditionLevel, purchaseTime, usageFrequency, source, warranty, supportTrial, images[], tradeType, location}` |
| PUT | `/goods/{id}` | 编辑（仅本人，已售出不可改）。字段同发布，规格字段传 `null` 表示清除 |
| POST | `/goods/{id}/status?status=` | 0 下架 / 1 上架 / 2 已售出 |
| DELETE | `/goods/{id}` | 删除（仅本人） |
| GET | `/goods/mine?status=` | 我发布的 |
| GET | `/goods/favorites` | 我收藏的 |
| POST | `/goods/{id}/like` | 收藏 / 取消收藏 → 返回最新 `liked` |
| POST | `/goods/{id}/order` | 提交购买意向（需认证）。body：`{contact, message?}` |
| GET | `/goods/orders/mine?role=buyer\|seller` | 我的求购 / 收到的意向（含 `goodsTitle`） |

规格字段取值：`conditionLevel` 1全新/2几乎全新/3轻微使用痕迹/4明显使用痕迹；`purchaseTime` 形如 `2023年9月`；`usageFrequency` 每天使用/经常使用/偶尔使用/几乎没用过；`source` 自购自用/朋友赠送/活动奖品/实验室配发/其他；`warranty` 官方保修中/官方保修已过/不确定；`supportTrial` 0否/1是。规格字段均为选填，老数据为 `null` 时详情页自动隐藏对应行。

## 匿名表白墙（campus-wall）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/wall/posts?page&size&schoolId&sort` | 🔓 列表（同校优先，返回 `anonName / anonAvatar / liked / mine`）。`sort=hot` 按点赞数倒序，默认按时间倒序 |
| POST | `/wall/posts` | 发布（需认证）。body：`{content, images[]}` |
| GET | `/wall/posts/{id}` | 🔓 详情 |
| PUT | `/wall/posts/{id}` | 编辑自己的表白（需认证）。body：`{content, images[]}`，匿名昵称与发布时间不变 |
| DELETE | `/wall/posts/{id}` | 删除自己的表白 |
| POST | `/wall/posts/{id}/like` | 点赞 / 取消 |
| GET | `/wall/posts/{id}/comments` | 评论列表（含 `imageList`、`mine`） |
| POST | `/wall/posts/{id}/comments` | 发表评论（需认证）。body：`{content, images[]}`，最多 3 张图，支持只发图 |
| DELETE | `/wall/posts/{postId}/comments/{id}` | 删除自己的评论（评论数同步 -1） |

> 响应中**不会**出现 `user_id`；匿名昵称与头像序号由服务端派生。

## 频道 / 帖子（campus-post）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/channels?schoolId` | 🔓 频道列表（全平台 + 本校），带 `postCount` |
| GET | `/posts?channelId&page&size&schoolId&userId` | 🔓 帖子列表（含 `channelName / channelIcon / imageList`）。**传 `userId` 时只返回该作者 `status=1` 帖子**，忽略学校范围，按时间倒序；不传时行为不变 |
| POST | `/posts` | 发帖（需认证）。body：`{channelId, title, content, images[]}` |
| GET | `/posts/{id}` | 🔓 详情 |
| PUT | `/posts/{id}` | 编辑自己的帖子（需认证）。body：`{channelId, title, content, images[]}` |
| DELETE | `/posts/{id}` | 删除自己的帖子 |
| POST | `/posts/{id}/like` | 点赞 / 取消 |
| GET | `/posts/{id}/comments` | 评论列表（含 `imageList`、`mine`） |
| POST | `/posts/{id}/comments` | 评论（需认证）。body：`{content, images[]}`，最多 3 张图，支持只发图 |
| DELETE | `/posts/{postId}/comments/{id}` | 删除自己的评论（评论数同步 -1） |

## 私信 / 消息中心（campus-message，8106）

全部接口需登录。会话成员固定为 `(userA = 小 id, userB = 大 id)`，
运行时按当前登录用户判定「我 / 对端」；同一 `用户对 + 商品` 只会有一个会话。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/messages` | 消息中心：`{ summary, convos }`。summary 含 `unreadTotal / likes / mentions / followers / notice`，convos 含对端昵称、身份标签、宝贝卡片快照、最后一条摘要（`lastText`）、最后一条发送方（`lastFrom: 'me'|'peer'`）与未读数 |
| GET | `/messages/unread` | 未读消息总数（首页铃铛角标）`{unread}` |
| POST | `/messages/start` | 发起或复用会话。入参 `{ peerId, goodsId?, goodsTitle?, goodsPrice?, goodsEmoji?, goodsStatus?, anon?, anonName? }`，返回会话对象 |
| GET | `/messages/{id}` | 会话详情（含聊天记录，按时间升序），进入即把对方消息标为已读 |
| POST | `/messages/{id}` | 发消息 `{content, type?}`，type 支持 `text / location / image`，返回更新后的完整聊天记录 |
| POST | `/messages/{id}/read` | 把整个会话标为已读 |
| GET | `/messages/{id}/messages` | 只取聊天记录，不触发已读 |
| DELETE | `/messages/{id}` | **删除会话（消息中心左滑）**。只对当前用户生效：写一条隐藏记录，对方会话与消息本体不受影响。幂等，重复调用仍返回成功 |
| DELETE | `/messages/{id}/messages/{messageId}` | **撤回一条消息（聊天页左滑）**。仅发送者本人可删自己的消息（删他人返回 403）；软删除，双方都不再看到。撤回的若是会话最后一条，会话摘要自动回滚到上一条 |

消息对象字段：`{ id, from: 'me'\|'peer', type, content, createdAt }`。

### 左滑删除的语义边界

| 场景 | 结果 |
| --- | --- |
| 删除会话 | 只写 `t_message_conversation_hide` 一行；`t_message_conversation` 与 `t_message` 数据不动，**对方完全不受影响** |
| 重复删除同一会话 | 幂等返回成功（`uk_user_conv` 唯一键兜底） |
| 删除后对方发来新消息 | 会话自动重新出现在列表中（发送时清除对端的隐藏记录） |
| 重新发起会话（`POST /messages/start`） | 自动取消隐藏，会话恢复显示 |
| 撤回消息 | 仅发送者本人可操作；他人消息返回 403；不存在的消息返回 404 |
| 撤回会话最后一条消息 | 会话摘要（`last_msg_id / last_content / last_time`）回滚到上一条存活消息 |

## 文件上传（campus-user）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/upload` | 需登录。`multipart/form-data`，字段名 `file`；限 5MB，支持 jpg/png/gif/webp；返回 `{url}`（已带网关地址，可直接用于 `image`） |
| GET | `/uploads/**` | 🔓 静态资源，由 user 服务直接回源，网关放行 |

## 内部接口（不经过网关）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/internal/users/login-or-create` | 按 openid 获取或创建用户（auth → user） |
| GET | `/internal/users/{id}` | 用户简要信息（昵称快照用） |
| GET | `/internal/users/briefs?ids=1,2,3` | 批量用户摘要（会话列表一次拉齐对端昵称/头像，避免 N+1；旧版本无此接口时自动退化为逐个查询） |
| GET | `/internal/goods/count?userId=&status=` | campus-goods：按作者计商品数，`status` 可选（不传=全部，已物理删除的不计）。供个人主页「闲置在售/累计发布」聚合 |
| GET | `/internal/posts/count?userId=` | campus-post：该作者 `status=1` 帖子数。供个人主页「帖子数」聚合 |
| POST | `/internal/admin/posts/{id}/status` | campus-post：后台改帖子 status（1正常 0隐藏） |
| DELETE | `/internal/admin/posts/comments/{id}` | campus-post：后台删评论，服务端回写 `t_post.comment_count` |
| POST | `/internal/admin/wall/{id}/status` | campus-wall：后台改墙帖 status |
| DELETE | `/internal/admin/wall/comments/{id}` | campus-wall：后台删墙评论 |
| POST | `/internal/admin/goods/{id}/status` | campus-goods：后台改商品 status；已售出（status=2）拒绝改回在售 |
| DELETE | `/internal/admin/goods/comments/{id}` | campus-goods：后台删商品评论 |
| POST | `/internal/admin/users/{id}/status` | campus-user：后台封禁/解封 |
| POST | `/internal/admin/users/{id}/mute` | campus-user：刷禁言投影 `mute_until`，传 null 表示解除 |
| POST | `/internal/admin/users/{id}/verify` | campus-user：实名审核结论（2通过 3驳回，驳回必填原因） |
| POST | `/internal/admin/messages/{id}/delete` | campus-message：后台删违规消息（软删 `deleted=1`，幂等） |

> 这一组 `/internal/admin/**` 由 campus-admin 调用，**不经过网关**，走 Nacos 内直连。
> 调用失败时 campus-admin 会降级为自己直写同一张表，最终结果一致。

## 后台管理（campus-admin 8107，走网关 `/api/admin/**`）

独立的**管理员体系**（`t_admin_user` + 独立 JWT），与小程序 C 端账号不互通。
完整接口清单、权限点、数据权限与跨服务降级策略见 **[docs/admin.md](./admin.md)**，
这里只列入口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/admin/auth/login` | 🔓 管理员登录，默认 `admin` / `admin123` |
| GET | `/api/admin/auth/info` `/auth/menus` | 当前管理员信息与可见菜单 |
| GET | `/api/admin/content/**` | 内容管理：帖子 / 表白墙 / 商品 / 评论 / 处置流水 |
| GET | `/api/admin/users/**` | 用户管理：列表、封禁、禁言、黑名单、实名审核 |
| GET | `/api/admin/reports/**` | 举报工单受理 |
| GET | `/api/admin/chat/**` | 会话与聊天记录查询、违规消息删除 |
| GET | `/api/admin/ads/**` `/notices/**` `/sensitive/**` | 广告位 / 公告 / 敏感词 |
| GET | `/api/admin/regions/**` | 校区地区树与负责人 |
| GET | `/api/admin/dashboard/overview` `/daily` | 数据看板 |
| GET | `/api/admin/logs/operations` `/logs/logins` | 操作日志 / 登录日志 |

自检：`node scripts/admin-smoke-test.js`（56 项）。

## 错误码

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 400 | 参数错误 / 业务校验失败（msg 为具体原因） |
| 401 | 未登录或 token 过期 |
| 403 | 无权限（如未完成校园认证、操作他人内容） |
| 404 | 资源不存在 |
| 500 | 服务端异常 |

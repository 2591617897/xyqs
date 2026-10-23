# 接口文档

- 统一入口：`http://127.0.0.1:8888/api`（网关端口 8888）
- 鉴权：`Authorization: Bearer <token>`；标注 🔓 的接口未登录也可访问
- 统一响应：`{ "code": 0, "msg": "ok", "data": ... }`，`code != 0` 即失败（401 未登录 / 403 未认证 / 404 不存在）
- 分页响应：`{ records: [], total, page, size, hasMore }`

## 认证 / 学校（campus-auth）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 🔓 登录。body：`{code, nickname?, avatar?, devOpenid?}`，返回 `{token, user:{id,nickname,avatar,schoolId,verifyStatus}}` |
| GET | `/schools?keyword=` | 🔓 学校字典（注册/认证必选），最多 50 条 |

## 用户（campus-user）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/user/me` | 我的资料（学号、姓名已打码，含 schoolName / verifyStatus / verifyRemark） |
| PUT | `/user/me` | 修改昵称、头像、性别、手机、简介 |
| POST | `/user/verify` | 提交校园认证。body：`{schoolId, studentNo, realName, verifyImg}` → `{verifyStatus, token, message}`（token 需替换本地） |

## 二手商品（campus-goods）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/goods` | 🔓 列表。参数：`keyword, categoryId, sort(latest/price_asc/price_desc/hot), page, size, schoolId` |
| GET | `/goods/categories` | 🔓 分类：数码/图书/生活/服饰/运动/其他 |
| GET | `/goods/{id}` | 🔓 详情（自动 +1 浏览量，登录时返回 `liked`） |
| POST | `/goods` | 发布（需认证）。body：`{title, description, price, originalPrice, categoryId, images[], tradeType, location}` |
| PUT | `/goods/{id}` | 编辑（仅本人，已售出不可改） |
| POST | `/goods/{id}/status?status=` | 0 下架 / 1 上架 / 2 已售出 |
| DELETE | `/goods/{id}` | 删除（仅本人） |
| GET | `/goods/mine?status=` | 我发布的 |
| GET | `/goods/favorites` | 我收藏的 |
| POST | `/goods/{id}/like` | 收藏 / 取消收藏 → 返回最新 `liked` |
| POST | `/goods/{id}/order` | 提交购买意向（需认证）。body：`{contact, message?}` |
| GET | `/goods/orders/mine?role=buyer\|seller` | 我的求购 / 收到的意向（含 `goodsTitle`） |

## 匿名表白墙（campus-wall）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/wall/posts?page&size&schoolId` | 🔓 列表（同校优先，返回 `anonName / anonAvatar / liked / mine`） |
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
| GET | `/posts?channelId&page&size&schoolId` | 🔓 帖子列表（含 `channelName / channelIcon / imageList`） |
| POST | `/posts` | 发帖（需认证）。body：`{channelId, title, content, images[]}` |
| GET | `/posts/{id}` | 🔓 详情 |
| DELETE | `/posts/{id}` | 删除自己的帖子 |
| POST | `/posts/{id}/like` | 点赞 / 取消 |
| GET | `/posts/{id}/comments` | 评论列表 |
| POST | `/posts/{id}/comments` | 评论（需认证）。body：`{content}` |

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

## 错误码

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 400 | 参数错误 / 业务校验失败（msg 为具体原因） |
| 401 | 未登录或 token 过期 |
| 403 | 无权限（如未完成校园认证、操作他人内容） |
| 404 | 资源不存在 |
| 500 | 服务端异常 |

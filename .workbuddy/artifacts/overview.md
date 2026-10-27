# 消息提醒功能完善

## 一句话结论

「消息设置」从 toast 占位变成能用的设置页；底栏角标从「只统计私信」改成「私信 + 通知」；订阅消息的授权链路已备好，后期绑服务号只需配模板 id，前端无需改动。

## 修掉的三个真问题

| 问题 | 原来 | 现在 |
|---|---|---|
| 消息设置 | `showToast('即将上线')` | 独立分包页面，5 类开关 + 免打扰 + 微信推送入口 |
| 防骗贴士 | 关闭只改内存，下次进页面又回来 | 写本地标记 `msg_tip_closed`，关了就真不再出现 |
| 底栏角标 | 只算私信未读，被赞/被关注毫无反应 | 私信 + 通知合计，并返回 `unreadMessage` / `unreadNotice` 分项 |
| 全部已读 | 只标记会话，四宫格红点还挂着 | 同时调 `/messages/notifications/read-all`，角标归零 |

## 后端新增

- 表 `t_message_setting`（migration 脚本 + schema.sql 同步）：5 类开关 / 免打扰起止 / 订阅授权状态
- 接口 `GET` `PUT` `/messages/settings`、`POST` `/messages/settings/subscribe?status=`
- **开关真的生效**：`NotificationService.unreadStats` 按类目过滤，且 `all` 由分项相加
- 免打扰时间走 `HH:mm` 白名单校验，非法值回退默认；`inDnd()` 支持跨天

## 前端新增

- `subpackages/msg/settings/index`：开关乐观更新 + 300ms 合并保存 + 失败回滚
- `utils/subscribe.js`：订阅消息封装，三条关键约束写在注释里

## 订阅消息为什么现在只做授权

| 环节 | 现在 | 绑服务号后 |
|---|---|---|
| 拿模板 id | 后端 `campus.wx.subscribe-templates`，未配则为空 | 配环境变量 `WX_SUBSCRIBE_TEMPLATES` |
| 请求用户授权 | ✅ 已实现（`wx.requestSubscribeMessage`） | 不用改 |
| 服务端发送 | ❌ 未实现 | 在 campus-auth 加发送服务，按开关 + 免打扰过滤 |

> 接口名 `subscribeMessage.send` 与参数以微信官方文档为准。access_token 获取能力已在 `WxSecurityService` 里有进程内缓存，可直接复用。

## 实测结果（走网关，真 token + 真数据）

| 操作 | 结果 |
|---|---|
| 造 3 条未读通知 | `all=3, like=1, follow=1, system=1` |
| 消息中心 summary | `unreadNotice=3, unreadTotal=3`（改前是 0） |
| 关闭「赞与收藏」 | like 立刻 0，all 变 2 |
| 传非法时间 `abc` / `99:99` | 回退 22:00 / 07:00 |
| 上报授权 status=1 | `subscribeStatus=1`，但 `wxPushOn` 仍 false（没模板不谎报） |
| 全部已读 | 清 3 条，随后角标全 0 |

测试数据已清理，8106 运行新代码。

## 回归

新增 `scripts/message-notify-selftest.js` **64 断言**（含 `subscribe.js` 六条真实分支：空模板不调接口 / 允许 / 拒绝 / 失败 / 超 3 个截断 / 脏数据过滤 / 同步调用）。

`check-miniprogram` 23+9 ｜ `mock-selftest` 154 ｜ `home-zones` 35 ｜ `msg-subpackage` ｜ `goods-search` 54 ｜ `search-multiword` 102 ｜ `hot-words` 76 ｜ `message-notify` 64 —— 全绿。

## 下一步（绑服务号时按这个顺序做）

1. 小程序后台「订阅消息」选模板，拿到模板 id
2. 配环境变量 `WX_SUBSCRIBE_TEMPLATES=tpl1,tpl2`（最多 3 个，上限以官方文档为准），重启 8106
3. 设置页会自动从「待开通」变成「开启」，用户点一下即走授权
4. 需要真推送时再在 campus-auth 加发送服务，按 5 个开关 + 免打扰过滤

---

## 闲置商品卡片信息补全（2026-10-27 第十五轮）

**问题**：内容文字少时卡片太空（用户截图：闲置交换列表页 + 首页闲置交换区）。

**解法**：把详情页才看得到的信息提前摊到卡片上 —— 地址 / 浏览量 / 想要 / 成色 / 描述摘要。

| 位置 | 改动 |
|---|---|
| `pages/goods/list.wxml` `.wxss` | 描述摘要 `.goods-desc`、成色 tag、底部 `.goods-stats`（📍地址 · 👁浏览 · 💗想要）；`.goods-title` 去掉写死的 `height:78rpx`（标题一行时原来下方空一大块），改竖向 flex + 统计行吸底 |
| `pages/index/index.wxml` `.wxss` | `.zcard-goods-desc`、`.zcard-goods-tags`（分类+成色）、`.zcard-goods-foot`（地址/浏览/想要）、划线原价 `.zcard-price-origin`；价格从「吸底」改为紧跟描述；图片改「定宽容器 + 绝对定位铺满」，图下方不留白边 |
| `utils/zones.js` | `goodsItem` 补 `conditionText` / `viewText` / `viewCount` / `commentText` / `showOrigin` |
| `pages/goods/list.js` | `decorate` 补 `descText` / `conditionText` / `viewText` / `likeText` |
| `app.wxss` | 新增 `.clamp1`（全局缺单行截断） |

**关键决策**

- **零后端改动**：`GET /goods` 本就返回 `location` / `viewCount` / `likeCount` / `conditionLevel` / `description` / `originalPrice`
- **「想要」= `likeCount`**（闲置的 like 语义是想要，不是帖子点赞），文案统一「想要 N」
- **划线原价仅在 `originalPrice > price` 时显示**（`showOrigin`），「原价 = 现价」是噪音（测试数据里就有）

**验证**：check-miniprogram / db-schema-check / security-baseline / mock / home-zones / msg-subpackage / goods-search 全部 PASS；JS 语法 OK；WXML handler 与标签配对通过。

---

## 首页树洞闲谈卡片改版（2026-10-27 第十六轮）

**问题**：截图反馈 —— 首页「树洞闲谈」区卡片右侧大片空白，点赞/评论挤在左下角；正文只有一行时卡片显得很空。

**解法**：卡片从「上下堆叠」改为 **左内容 + 右竖排互动栏**，点赞按要求移到右边。

| 位置 | 改动 |
|---|---|
| `pages/index/index.wxml` | 树洞卡片重写：`.wall-main`（左：头像行 + 标签 + 正文 + 配图）/ `.wall-side`（右：💗赞 · 💬评 · ↗分享 竖排）；徽标提到昵称旁成胶囊；副行改「时间 · 匿名保护中」；补 `.wall-imgs` 配图渲染 |
| `pages/index/index.wxss` | 新增 `.wall-main` `.wall-side` `.wall-act` `.wall-act-ico` `.wall-act-num` `.wall-share` `.zcard-name-row` `.zcard-flair` `.zcard-tags` `.wall-imgs` `.wall-img`；`.zcard-wall .zcard-excerpt` 改浅灰底色块 |
| `pages/index/index.js` | `onShareAppMessage` 支持按 `data-id` 分享**具体树洞帖子**（标题取正文摘要、路径 `/pages/wall/detail?id=`），之前固定分享首页 |

**关键决策**

- **点赞放右边 = 竖排互动栏**，不是简单右对齐 —— 单列全宽卡片的右边天然是空位，竖排栏把空间填上而不是白白留着
- **正文改浅灰底色块**（`#f7f8fa` + 圆角 + padding）：一行字也有视觉重量，是解决「太空」最直接的手段
- **补了配图渲染**：首页树洞卡片之前完全没渲染 `imageList`，带图的心事等于丢了图
- 分享按钮用 `catchtap="noop"` 阻止冒泡 —— 否则会连带触发卡片外层的 `bindtap="goZoneDetail"`

**验证**：check-miniprogram / db-schema-check / security-baseline / mock / home-zones / msg-subpackage / goods-search 全部 PASS；JS 语法 OK；WXML handler 与标签配对通过；网关 200。**零后端改动，无需重启服务。**

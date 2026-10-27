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

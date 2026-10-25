# 校园聚 · 内容安全（文本 + 图片）

> 目标：满足微信「UGC 小程序必须接入内容安全 API，且在小程序内**任意发布场景**生效」的审核要求。
> 校验脚本：`node scripts/content-security-check.js`

## 1. 一句话架构

```
小程序 ──POST /api/...──▶ campus-gateway ──▶ 业务服务（post / wall / goods / message / user）
                                              │ 落库前一行：SecurityGuard.checkText(...)
                                              ▼ Feign /internal/security
                                          campus-auth　⇄　api.weixin.qq.com
                                          （唯一持有 appid/appsecret + access_token 缓存）
                                              ▲
微信「消息推送」──POST /api/wx/callback───────┘（图片审核结果异步回调）
```

所有微信调用都收在 `campus-auth`：只有它知道 appsecret，业务服务只发一行 Feign。

## 2. 覆盖矩阵

| 场景 | 业务类型 | 文本（同步拦） | 图片（异步送审） |
|---|---|---|---|
| 发帖 / 编辑 | `POST` | ✅ SCENE_FORUM | ✅ |
| 帖子评论 | `P_COMMENT` | ✅ SCENE_COMMENT | ✅ |
| 表白墙发布 / 编辑（匿名） | `WALL` | ✅ SCENE_FORUM | ✅ |
| 表白墙评论 | `W_COMMENT` | ✅ SCENE_COMMENT | ✅ |
| 闲置发布 / 编辑 | `GOODS` | ✅ SCENE_FORUM | ✅ |
| 闲置评论 | `G_COMMENT` | ✅ SCENE_COMMENT | ✅ |
| 私信（文本） | `MESSAGE` | ✅ SCENE_FORUM | — |
| 私信（图片） | `MESSAGE` | — | ✅ |
| 昵称修改 | `NICKNAME` | ✅ SCENE_PROFILE | — |
| 私信（视频） | — | ❌ 不送审 | 微信 `media_check_async` 不支持视频（仅图片/音频） |

**新增发布入口时必须两件事**：调 `securityGuard.checkText(...)`、带图再调 `checkImages(...)`，
然后把它加进 `scripts/content-security-check.js` 的 `ENTRIES` 里——那个脚本就是防漏的闸门。

## 3. 两个接口的差别（决定了实现方式）

| | 文本 `msg_sec_check` v2 | 图片 `media_check_async` v2 |
|---|---|---|
| 结论 | **同步**返回 `suggest = pass/review/risky` | **异步**，只返回 `trace_id` |
| 能否发布前拦住 | 能（`risky` 直接 400，不落库） | **不能**，只能先发后审 |
| 结果如何回来 | 本次响应 | 微信「消息推送」回调 |
| 我们怎么处理 | `recordViolation(REJECT)` + 抛 400 | 落 `t_media_check` → 回调更新状态 → 写 `t_content_audit` 待运营下架 |

`suggest = review`（疑似）的策略：**放行 + 留痕**（`action=WARN`），不阻塞用户；证据留在 `t_content_audit` 里，便于申诉举证与事后复核。

## 4. 处置记录

违规事件统一写 `t_content_audit`（复用后台已有的「内容审核记录」页面）：

| 字段 | 值 | 说明 |
|---|---|---|
| `source` | `2` | 系统自动（1 后台人工 / 2 系统自动 / 3 举报处理） |
| `action` | `REJECT` / `WARN` | 拒绝发布 / 疑似放行 |
| `target_type` | `POST`/`WALL`/`GOODS`/…/`MESSAGE`/`NICKNAME` | 业务类型 |
| `target_id` | `0` 或真实 id | 文本拦截时内容未落库 → 0；图片回调能拿到 → 真实 id |
| `reason` | 例：`图片审核：risky label=20002` | 微信原始判定留档 |
| `content_brief` | 前 200 字 / 图片 URL | — |

图片台账 `t_media_check`：`trace_id` 唯一（回调幂等），`status` 0 待审 / 1 通过 / 2 疑似 / 3 违规 / 4 送审失败。

## 5. 上线配置清单

### 5.1 服务端

```bash
WX_APPSECRET=<小程序 appsecret>
WX_SEC_CHECK=true           # 内容安全总开关（默认 false）
WX_MEDIA_CHECK=true         # 图片异步审核（默认跟随总开关）
WX_CALLBACK_TOKEN=<自定义 32 位随机串>
```

`campus-auth/application.yml` 已接线：`campus.wx.sec-check.enabled / media-enabled / callback-token`。

### 5.2 微信公众平台

1. **IP 白名单**：`开发管理 → 开发设置 → 服务器域名 / IP 白名单`，把**服务器出口 IP** 加进去。
   不加必报 `errcode=40164`，表现为「所有内容都不审、全部放行」（fail-open 会把错误掩盖成正常）。
2. **消息推送**（图片结果回调必需）：
   - URL：`https://<域名>/api/wx/callback`
   - Token：与 `WX_CALLBACK_TOKEN` 一致
   - 数据格式：**JSON**（XML 无法解析媒体审核事件，会打 warn 日志）
   - 加密方式：**明文模式**（安全模式需 AES 解密，本项目未实现）
3. 保存后微信会立即 GET 一次该 URL 验签，返回 `echostr` 才算通过。

### 5.3 验证

```bash
# 开关与凭据状态（Feign 直连 /internal/security/enabled，需绕过网关）
curl http://<auth-host>:8101/internal/security/enabled
# → {"code":0,"data":{"text":true,"media":true,"callback":true}}
```

发一条含敏感词的帖子，应看到：HTTP 400 + 「内容涉嫌违规，请修改后再发布」，
且 `t_content_audit` 多一条 `source=2, action=REJECT`。

## 6. 已知边界（写清楚，不藏）

- **fail-open**：微信侧超时/配额/异常时一律放行并打 warn。审核是增强能力，不能让它变成
  「auth 挂了全站发不出内容」的单点故障。生产要盯 `[sec-check]` / `[sec-media]` 日志错误率。
- **图片先发后审**：违规图片在回调到达前已经可见（几十秒到几分钟）。回调命中后只落台账 +
  处置流水，**下架由运营在后台执行**（`后台 → 内容管理 → 帖子/商品 隐藏`）。
  没做「回调自动下架」是为了不让 auth 反向写业务库，避免服务边界与事务问题。
- **视频不送审**：微信媒体审核不支持视频；私信视频靠举报 + 人工处置。
- **本地联调**：`media_url` 必须公网可达，`localhost` / 局域网 IP 送审必然失败（会写 status=4），
  本地可置 `WX_MEDIA_CHECK=false` 关掉。
- **review 不拦**：仅留痕。若运营发现某类 review 命中率过高，再考虑改成拦截。

# 校园趣事 · 架构设计

## 1. 整体形态

```
QQ 小程序端 (WXML/WXSS/JS)
        │  HTTPS  /api/**
        ▼
campus-gateway :8888      ← Spring Cloud Gateway：路由 + JWT 鉴权 + 注入用户上下文
        │  lb://
        ├── campus-auth   :8101   微信/QQ code2Session 登录取 openid、JWT 签发、学校字典
        ├── campus-user   :8102   用户资料、实名/学号身份认证（核心准入门槛）
        ├── campus-goods  :8103   二手商品发布/浏览/收藏/购买意向
        ├── campus-wall   :8104   匿名表白墙（匿名昵称派生 + 内部留痕）
        └── campus-post   :8105   频道分区 + 帖子 + 评论区
                 │
        MySQL 8（guizhou_campus_fun） / Redis 7 / Nacos（注册与配置中心）
```

## 2. 关键设计决策

| 关注点 | 方案 | 原因 |
| --- | --- | --- |
| 微服务拆分 | 按业务域拆（认证/用户/商品/表白墙/内容） | 低耦合，二手交易与内容消费压力模型不同，可独立扩容 |
| 网关鉴权 | JWT 在网关统一校验，向下游透传 `X-User-Id / X-School-Id / X-Verify-Status` | 业务服务不重复解析 Token；网关过滤客户端伪造的同名 Header |
| 服务调用 | auth → user 走 OpenFeign（`/internal/**` 内部接口，不对外路由） | 登录时 get-or-create 用户，避免 auth 直接写用户表 |
| 读模型 | 商品/帖子上冗余 `seller_name / author_name / avatar` 快照 | 列表页无需 N+1 跨服务调用，典型 CQRS 取舍 |
| 匿名性 | `t_wall_post.user_id` 只入库不返回；对外用 `anon_name`（由 user_id+post_id 派生，稳定不可反推） | 兼顾「可匿名」与「可追溯」的合规底线 |
| 身份门槛 | 注册即要求 `school_id + 学号 + 真实姓名 + 学生证照片`，`verify_status=2` 才允许发布 | 校园社区的信任基础，浏览可放开、发布必须实名 |
| ID 生成 | 雪花 ID（MyBatis-Plus `ASSIGN_ID`） | 分库分表友好，不泄露业务量 |

## 3. 身份认证流程（重点）

```
首次进入小程序
  └─ qq.login() → code
      └─ POST /api/auth/login {code}
          └─ auth: code + platform → jscode2session → openid
              └─ Feign: /internal/users/login-or-create (platform, openid)
                  └─ 返回 userId / schoolId=null / verifyStatus=0
          └─ 签发 JWT (userId, schoolId, verifyStatus)
→ 前端检测 verifyStatus != 2 → 强制跳转认证页
  └─ POST /api/user/verify {schoolId, studentNo, realName, verifyImg}
      └─ 校验学号格式、学校存在性、是否被占用 → verify_status=1（审核中）
      └─ 审核通过后 verify_status=2，JWT 需重新签发（重新登录或刷新接口）
```

> 生产环境建议接入「学信网/校方统一身份」接口或人工审核台；本项目用 `campus.verify.auto-approve=true`
> 在本地直接置为已认证，方便联调。

## 3. 邮件服务与消息队列

邮件是**基础设施能力**，不该散落在各业务服务里——否则 SMTP 凭据会被复制到每个服务，
邮件版式也会随人肉复制漂移成好几种。因此单独拆出 `campus-mail :8109`：

```
任一业务服务 ──publish──▶ campus.mail (direct)
                              └─ mail.send ──▶ campus.mail.send
                                                 └─ campus-mail 消费 → 渲染模板 → SMTP
                                                      失败重试 3 次 → campus.mail.dlq
```

| 约定 | 内容 |
| --- | --- |
| 消息体 | `campus-common` 的 `MailMessage`：`{requestId, to, template, params, bizType, bizId}`，**不含 HTML** |
| 队列名 | `campus-common` 的 `MailQueues` 常量，生产方与消费方共用，避免拼错后消息静默丢失 |
| 模板 | `MailTemplates` 只放编码，正文由 `campus-mail` 的 `MailTemplateRegistry` 生成 |
| 幂等 | `t_mail_log.request_id` 唯一索引；MQ 至少一次投递，重复消息直接跳过 |
| 失败 | 消费者异常向上抛，交 AMQP 重试；**绝不 catch 掉**——否则 MQ 认为已消费，邮件静默丢失 |
| 观测 | `t_mail_log` 是「到底发出去没有」的唯一事实来源，可用 `GET /internal/mail/logs/{requestId}` 查 |

队列拓扑在**生产端与消费端各声明一份**（参数必须逐字一致）：生产端声明是为了避免
「消息发到还不存在的 exchange 上，AMQP 不报错、消息直接消失」。

## 4. 扩展方向

- **搜索**：商品/帖子接入 Elasticsearch，关键词与同义词（如「显示器」≈「屏幕」）
- **IM**：二手交易的即时沟通可接腾讯云 IM，替代当前「联系方式透出」
- **风控**：表白墙内容过敏感词与图片审核（内容安全 API），命中先审后发
- **拆分**：`t_like` 建议按 `target_type` 拆到各服务库，或独立互动服务 + Redis 计数

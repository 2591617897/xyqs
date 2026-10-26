# 并发 / 缓存 / 消息队列 架构审计报告

> 审计对象：campus（校园趣事）后端 9 模块 + 网关 + 管理台
> 审计日期：2026-10-26
> 审计方式：全量代码走查（非推测），每条结论附文件与行号

---

## 0. 一句话结论

**架构是「单实例 + 无缓存 + 无消息队列」的纯直连模式**：所有读请求直打 MySQL，所有写请求在数据库事务内**同步**调用外部 HTTP（微信审核、Feign）。

两个硬瓶颈决定了真实并发上限：

1. 每个服务 Hikari 连接池固定 **10** 个连接
2. 事务内同步调用微信 `msg_sec_check`（超时 **8s**）+ 图片送审

**Redis 容器已经在 `docker-compose.yml` 里跑着（6379），但代码里零使用。MQ 完全没有。**

单服务有效吞吐 ≈ `10 连接 ÷ 事务平均持有时长`：

| 场景 | 事务持有时长 | 单服务理论吞吐 |
|---|---|---|
| 微信审核正常（~200ms） | 0.25s | ~40 TPS |
| 微信审核慢（2s） | 2.1s | ~4.8 TPS |
| 微信审核超时（8s） | 8.3s | **~1.2 TPS，且连接池 8 秒内被占满** |

也就是说：**发帖/评论/私信这条链路上，微信一慢，整个 campus-post / goods / wall / message 服务全体雪崩**，不只是发布接口变慢——连接池是共享的，查列表的请求也拿不到连接。

---

## 1. 必须上 Redis 的位置

### P0-1　微信 access_token 集中存储 ⚠️ 当前就是故障点

| 项 | 内容 |
|---|---|
| 位置 | `campus-auth/.../service/WxSecurityService.java:46`（注释明写"全仓没有 Redis，用进程内缓存"） |
| 现状 | access_token 存 JVM 内存，7200s 提前 300s 过期 |
| 问题 | ① **多实例互相踢**：A 实例拉新 token，B 实例手里的旧 token 立刻失效（微信是单 token 模型），审核全部 fail-open 静默放行，症状是"配好了却拦不住" ② 服务重启后每次都重新拉，微信有日调用配额 ③ 各实例的过期时间不同步 |
| 方案 | `SET wx:access_token <token> EX 7000 NX` + 拉取加分布式锁（SETNX），全集群一份 |
| 影响面 | 内容安全完全失效（fail-open 会把违规内容直接放行） |

### P0-2　未读角标 / 消息中心统计

| 项 | 内容 |
|---|---|
| 位置 | `MessageService.java:117-126`（summary）、`:228`（unreadTotal）；调用方是 `custom-tab-bar`（每次 `pageLifetimes.show` 都拉） |
| 现状 | `conversationMapper.countUnread` + `messageMapper.countReceivedLikes`（**t_like JOIN t_goods / t_wall_post 跨表聚合 count**）+ `countReceivedComments` |
| 问题 | 这是**全站调用频率最高的接口**（每次切 tab 都打）。3 个聚合 count，随数据量线性变慢；tab 切换是用户最高频动作，QPS ≈ DAU × 8~15 |
| 方案 | ① 未读数用 Redis Hash `msg:unread:{uid}` → `{convId: n}`，读 O(1) ② 收到新消息时 `HINCRBY`，进入会话时 `HDEL` ③ `likes/mentions` 改为在 t_notification 上加 `is_read` 计数缓存，30~60s TTL |

### P0-3　首页运营位 + 频道字典（cache-aside）

| 项 | 内容 |
|---|---|
| 位置 | `OperateService.java:46-97`（广告位 + 公告）、频道列表、`GoodsService.CATEGORIES`、学校字典 |
| 现状 | 首页一次 `Promise.all` 打 6 个接口，每个接口内部又是 2~5 条 SQL；`selectSlotMaxCount` 每次先查配置再查素材 |
| 问题 | 运营配置类数据变更频率是「天级」，却被千万次级读取 |
| 方案 | `ad:slot:{code}` TTL 300s + `notice:{schoolId}` TTL 120s；后台 `campus-admin` 写完**主动 DEL**。学校字典/频道表 TTL 600s |
| 收益 | 首页首屏 SQL 从 ~20 条降到 ~6 条 |

### P0-4　详情页浏览量热行（view_count 写扩散）

| 项 | 内容 |
|---|---|
| 位置 | `GoodsService.java:151`、`PostService`、`WallService` 每次打开详情都 `update ... set view_count = view_count + 1` |
| 问题 | 同一篇爆款内容被 1000 人同时看 → 1000 次行锁排队（InnoDB 行锁是串行的），详情页 RT 会被同一条行的写锁拖成线性增长 |
| 方案 | Redis `INCR view:{type}:{id}` 缓冲，定时任务（每 30s 或累计 200 次）批量回写；读取时 `DB 值 + Redis 增量` |
| 备选（成本更低） | 只对「近 7 天内容」计数，老内容不再自增 |

### P0-5　写接口限流（防刷）

| 项 | 内容 |
|---|---|
| 位置 | 全站缺失。网关 `campus-gateway/src/main/resources/application.yml` 无任何 `RequestRateLimiter`，无 Sentinel |
| 问题 | 发布/评论/点赞/私信/上报**无任何频率控制**，一个脚本可以：刷点赞（直接打爆 t_like + 计数行锁）、刷私信（打爆消息表）、刷举报 |
| 方案 | 网关层按路由 + 用户维度 Redis 令牌桶：发帖 5/分钟、评论 20/分钟、点赞 60/分钟、私信 30/分钟；登录 10/分钟/IP。用 `spring-cloud-starter-gateway` 自带 `RequestRateLimiter` + `RedisRateLimiter`，改 yml 即可 |
| 备注 | 这是**并发问题里唯一不需要改业务代码、收益最大的一项** |

### P0-6　JWT 吊销 / 封禁名单

| 项 | 内容 |
|---|---|
| 现状 | JWT 纯无状态，72 小时有效。封号后仅靠「登录快照重签」失效，旧 token 仍能用到过期；`schoolId`/`verifyStatus` 也在 token 里（记忆里已知痛点） |
| 问题 | 后台封禁用户 → 最长 72 小时才真正生效。这是安全缺陷，也影响一致性（改学校后前端看到的还是旧值） |
| 方案 | Redis `auth:tver:{uid}` 存 token 版本号，签发时写入 JWT claim；网关校验版本一致。封号/改学校/改认证状态时 `INCR` 即全端失效 |
| 附带收益 | 顺手解决「改库后必须重签」的老问题，不用再让前端 `ensureLogin(true)` |

### P1 级（有余力再做）

| # | 位置 | 问题 | 方案 |
|---|---|---|---|
| P1-1 | 列表页 `hasLiked` | 每行一条 SQL / 或整页 N 条 | Redis Set `like:{type}:{targetId}` 或 SQL `IN` 批量 |
| P1-2 | `LocalSensitiveGuard.java:77` | 每实例各自 60s 轮询 t_sensitive_word，N 个实例 N 倍查询，且更新有 0~60s 时间差 | Redis pub/sub `sensitive:reload` 广播 + 本地 Caffeine 二级缓存 |
| P1-3 | 首页「今日最热」 | `order by view_count desc`，无覆盖索引，数据量大后 filesort | Redis ZSET `hot:daily:{date}`，定时或读时累加 |
| P1-4 | `AdminAuthInterceptor.java:49` | 权限进程内缓存 60s，多实例权限变更不同步 | 同 P1-2 方案 |
| P1-5 | 定时任务 | 目前 `UserManageService.java:214` 注释"P2 接 @Scheduled"；一旦多实例会重复执行 | Redis 分布式锁 `SET lock:job:{name} NX EX` |

---

## 2. 必须上 MQ 的位置

**现状：全仓 0 个 MQ，16 处 `CompletableFuture.runAsync` 硬扛异步。**

统一问题：用的是 `ForkJoinPool.commonPool()`（默认并行度 = CPU 核数 - 1，通常 3~7），而且：
- 无隔离：慢 Feign 把 commonPool 占满会拖累所有使用者（包括 `parallelStream`）
- 无重试：一次网络抖动 → 通知永久丢失
- 无持久化：服务重启 → 内存里的待发通知蒸发
- 无死信：失败只有一行 warn 日志

### P0-1　通知系统（当前最大坑）

| 项 | 内容 |
|---|---|
| 位置 | `GoodsService.java:276/385/405/428`、`PostService.java:267`、`WallService.java` 点赞/评论/回复/@ 通知、`FollowService.java:89` |
| 现状 | 事务内 `CompletableFuture.runAsync(() -> notificationClient.send(req))` |
| 三个具体故障 | ① **通知丢失**：用户发评论后正好服务重启 → 作者永远收不到 ② **通知多发**：CompletableFuture 在事务提交**前**就发出去了，若主事务随后回滚（比如图片审核抛错），通知已飞 ③ 顺序错乱 + 无幂等，同一个赞反复点能生成多条通知 |
| 方案 | RabbitMQ（或 RocketMQ）`notify.exchange`，topic 按 type 路由；生产者用**本地消息表**（`t_outbox` 与业务同一个事务写入，定时扫表投递）保证"业务成功 → 通知一定发"；消费者按 `uk(biz_type, biz_id, receiver_id)` 幂等 |
| 关键收益 | 事务内不再有网络调用 → 事务持有时长从"HTTP 往返"降到"纯 DB"，直接解开 0 节说的吞吐死结 |

### P0-2　内容审核异步化

| 项 | 内容 |
|---|---|
| 位置 | `SecurityGuard.checkText` 被所有 UGC 写入口在**事务内**调用（`GoodsService:182`、`PostService:205`、`WallService`、`MessageService:184`…） |
| 现状 | 同步 HTTP 到微信，超时 8s（`WxSecurityService.java:462/517`） |
| 问题 | 这是**全站最贵的一次调用，却在最不该的位置（事务内）**。审核 200ms 尚可，遇到微信抽风 8s，一个发布请求就占住一个连接 8 秒 |
| 方案（推荐，语义不变） | 本地敏感词 `purify` **保留同步**（纯内存，微秒级）；微信 `msg_sec_check` 改为**先发后审**：内容先落库 `status=审核中`，投 MQ，审核通过 `status=正常`，违规则 `status=隐藏` + 写 `t_content_audit`。`media_check_async` 本来就是这个模型，文本对齐到图片即可 |
| 方案（保守，改动小） | 保持同步拦截，但把超时从 8s 降到 1.5s，并把 `checkText` 移出 `@Transactional` 方法（先审后开事务） |

### P0-3　计数回写

配合 Redis P0-2 / P0-4：点赞数、浏览数、未读数在 Redis 累加，由定时任务或 MQ 批量刷回 MySQL（`UPDATE ... SET c = c + delta`），把高频小事务合并成低频批量写。

### P1 级

| # | 场景 | 说明 |
|---|---|---|
| P1-1 | 点赞通知聚合 | LinkedIn 式「A、B 等 5 人赞了你」，需要窗口聚合 → 天然是 MQ delay queue |
| P1-2 | 审核流水落库 | `AsyncAuditWriter.java:58` 现在是**内存队列 + 满了就丢**（`queue.offer` 失败只打日志），进程重启全丢。审计合规要求不能丢 → 改投 MQ 持久化 |
| P1-3 | 后台报表 | `t_dashboard_daily` 聚合、`UserManageService` 提到的定时任务，全部走 MQ + 消费者，避免多实例重复跑 |
| P1-4 | 搜索索引同步 | 现在是 `like` 全表模糊查询，数据量上来自建 ES 时需要 binlog/MQ 同步 |

**选型建议**：项目是 Spring Cloud 全家桶 + 单库，`RabbitMQ` 最省事（Spring AMQP 开箱、延迟队列/死信/TTL 齐全、运维简单）。如果预期未来要做大规模日志/埋点，选 `RocketMQ`（事务消息原生支持，正好对 P0-1 的本地消息表）。**不要上 Kafka**——这个体量用不上，运维成本反而更高。

---

## 3. 已修复的数据一致性问题（本次改动）

### ✅ 3.1　点赞/收藏并发取消导致计数永久漂移（严重）

**缺陷代码**（三处一模一样）：

```java
if (exist != null) {
    likeMapper.deleteById(exist.getId());   // ← 不检查影响行数
    goodsMapper.incrLike(goodsId, -1);      // ← 照样 -1
    return false;
}
```

两个请求同时取消同一个赞：都读到 `exist != null` → 都执行 delete → 只有第一个真正删掉行（第二个影响 0 行）→ **但两个都执行了 `-1`**。

结果：`like_count` 比真实值少 1，而且**永远不会自愈**。`greatest(x+delta, 0)` 只能防负数，防不了漂移。

现场参考：`FollowService.java:113` 的 `unfollow` 是**正确写法**（`if (removed > 0)` 才动计数），说明点赞这里是遗漏而非设计。

**修复**（`GoodsService.java:242` / `PostService.java:231` / `WallService.java:170` 同步改）：

```java
if (exist != null) {
    // 并发双取消：只有真正删掉关系行的那次才回退计数
    if (likeMapper.deleteById(exist.getId()) > 0) {
        goodsMapper.incrLike(goodsId, -1);
    }
    return false;
}
```

同时补上并发**点赞**的唯一键兜底（`uk_target_user`）：

```java
try {
    likeMapper.insert(like);
} catch (DuplicateKeyException e) {
    return true;   // 关系行已由并发事务建立，不重复 +1
}
```

> 修复前：并发双击 → 一方 HTTP 500（用户看到"操作失败"）；修复后：幂等返回 `true`。

### ✅ 3.2　并发登录建号撞唯一键导致 500

`UserService.loginOrCreate`：小程序冷启动时，首页 `ensureLogin()` 与「我的」页 `ensureLogin()` 可能**同时**发起登录 → 两个请求都读到 `user == null` → 都 `insert` → 撞 `uk_platform_openid` → **其中一个 500**。用户偶发"登录失败"，刷新又好。

修复：catch `DuplicateKeyException` 后重新 `select` 回已建好的用户。

### ✅ 3.3　验证结果

4 个模块（goods / post / wall / user）`mvn compile` 通过，无告警。

---

## 4. 待决策的数据一致性问题（需你确认业务语义）

### 4.1　商品状态并发覆盖（建议修）

`GoodsService.changeStatus` 用 `updateById(goods)` **全量覆盖**，无状态机校验：

```
买家下单 → 卖家标记"已售出"(status=2) → 同时卖家在另一个页面点"下架"(status=0)
```

后写覆盖先写，可能出现"已售出被改回在售"，导致重复交易。

**建议**：改成条件更新 + 校验影响行数，并定义合法状态迁移表（1→2 只能单向）：

```java
@Update("update t_goods set status = #{to} where id = #{id} and user_id = #{uid} and status = #{from}")
int casStatus(...);
```

### 4.2　`createOrder` 无并发保护

两个买家同时下同一件商品都会成功。如果"购买意向"业务上允许，这没问题；但需要**确认商品已售出时是否要拒单**——目前只在进入时检查 `status == 1`，检查与写入之间无锁。

### 4.3　跨服务"一致性"其实是单库

所有服务连同一个库 `guizhou_campus_fun`，所以 `@Transactional` 在单服务内是可靠的。但：

- `GoodsService.addComment` 事务内调 `userClient.getBrief`（Feign）+ `securityGuard.checkText`（微信 HTTP）→ **长事务持锁**，这是吞吐杀手，不只是性能问题
- `MessageMapper.countReceivedLikes` 直接 JOIN 了 goods / wall 的表 → **隐含跨服务数据耦合**，将来拆库必炸

**建议**：Feign/HTTP 调用一律移出事务边界（先查后开事务，或改为异步补齐）。这条比上 Redis 更限时——它不需要新组件就能拿收益。

---

## 5. 并发瓶颈排序（按"改一处收益最大"）

| 排名 | 瓶颈 | 位置 | 单点改造量 |
|---|---|---|---|
| 1 | 事务内同步调用微信审核（8s 超时） | 全部 UGC 写入口 | 中 |
| 2 | 6 个服务 Feign 超时用默认值（read 60s！） | 见下 | **极小（改 yml）** |
| 3 | 未读角标接口无缓存 | `MessageService:117` | 小 |
| 4 | 详情页 view_count 热行写 | 三个 Service | 小 |
| 5 | 全站无限流 | 网关 | **极小（改 yml）** |
| 6 | 个人主页 5 次串行 Feign count | `UserService:275-337` | 小 |
| 7 | 通知走 commonPool、无重试 | 16 处 | 大（需 MQ） |

### ⚠️ 第 2 名是个"1 分钟就能修、不修会团灭"的雷

只有 `campus-message` 配了 Feign 超时（`connect 3s / read 5s`），**另外 6 个服务（auth / user / goods / post / wall / admin）全部用默认值 `connect 10s / read 60s`**。

意味着：任何一个下游服务 GC 停顿或慢查询 → 调用方 60 秒不释放连接 → Hikari 10 连接在 600 秒内全部打满 → **该服务所有接口（包括最简单的 GET）全部超时**。这是最典型的微服务雪崩传导路径，而且扩散链是 Feign 网状调用（user ↔ goods ↔ post ↔ wall）。

最小修复（`campus-common` 或各服务 yml 统一加）：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 3000
```

---

## 6. 落地路线（建议按这个顺序，每阶段都可独立上线）

### 阶段一：不加新组件，先止血（半天，纯配置 + 局部代码）

1. 6 个服务补 Feign 超时（2s / 3s）
2. 网关加限流（Redis 容器已在跑）
3. 微信审核超时 8s → 1.5s；`checkText` 移出 `@Transactional`
4. 已完成的 3 个一致性修复上线
5. Hikari `maximum-pool-size` 10 → 20（单库总连接控制在 MySQL `max_connections` 的 70% 以内）

**这一阶段不改架构，但能立刻把并发上限从"个位数"提到"几十"级。**

### 阶段二：接 Redis（2~3 天）

按 P0-1 → P0-2 → P0-3 → P0-4 → P0-6 顺序。每接一项用 `curl` 对比改动前后 SQL 执行次数（开 `logging.level.com.campus=debug` 数 `Preparing:` 条数）。

### 阶段三：接 MQ（3~5 天）

先做通知系统（含 `t_outbox` 本地消息表），再做审核异步化，最后做计数回写。消费者的幂等键必须在**第一天**就设计好，否则后面全是补丁。

---

## 7. 怎么验证改完真的有效

```bash
# 1. 并发取消点赞，看计数是否守恒（修复前必漂移）
seq 20 | xargs -P 20 -I{} curl -s -X POST localhost:8888/api/goods/1/like -H "Authorization: Bearer $TOKEN"

# 2. 并发登录，看是否还有 500（修复前必现）
seq 10 | xargs -P 10 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST localhost:8888/api/auth/login -d '{"code":"test","platform":"wx"}'

# 3. 数一次首页首屏的 SQL 条数（接缓存前后对比）
#    日志里 grep "Preparing:" 计数

# 4. 事务持有时间（阶段一的主要指标）
#    开 logback 的 DataSource 代理，或看 Hikari 的 activeConnections 峰值
```

---

## 附：审计覆盖清单

| 模块 | 检查项 | 结论 |
|---|---|---|
| campus-gateway | 限流 / 熔断 / 重试 | ❌ 全部缺失 |
| campus-auth | token 缓存 / 审核超时 | ⚠️ 进程内缓存、8s 超时 |
| campus-user | 计数一致性 / 建号并发 | ✅ 已修建号；⚠️ 5 次串行 Feign |
| campus-goods | 点赞一致性 / 订单并发 / 热行 | ✅ 已修点赞；⚠️ 状态无 CAS |
| campus-post | 点赞一致性 / 运营位缓存 | ✅ 已修点赞；⚠️ 曝光循环单条 UPDATE |
| campus-wall | 点赞一致性 | ✅ 已修 |
| campus-message | 未读统计 / 摘要并发 | ⚠️ 跨表聚合 count，无缓存 |
| campus-admin | 权限缓存 / 定时任务 | ⚠️ 进程内缓存，多实例不同步 |
| campus-common | Feign 超时 / 审核 SPI | ⚠️ 无全局超时兜底 |
| 全部 | Redis 使用 | ❌ 0 处（容器已就绪） |
| 全部 | MQ 使用 | ❌ 0 处（16 处 commonPool 硬扛） |
| 全部 | 数据库索引 | ✅ 关键唯一键齐全（uk_platform_openid / uk_pair / uk_target_user / uk_pair_goods） |

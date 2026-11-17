# 通知网关 · 会话与游标设计

> 成员 6 ｜ 2026-11-17 ｜ 状态：**设计定稿，尚未实现** ｜ 生效范围：W4（轮询，A0）、W6（WebSocket，A1）

## 1. 范围

| 网关负责 | 网关不负责 |
|---|---|
| 维持会话、按游标增量拉取、投递、断线补拉 | 不写业务表（只读通知）；不做用户认证，JWT 透传给 service 校验 |
| 限制多开、回收僵尸会话 | 不做已读标记（客户端直接调 service `/notifications/read`） |
| 记录每会话投递游标 | 不做业务规则判定（预约/扣分/释放） |

数据来源：`GET /api/v1/internal/notifications?userId&afterId&limit`，头 `X-Internal-Token`（见 `contracts/openapi.yaml:567`）。**网关不连数据库**，避免 D 机直连 A 机 MySQL。

## 2. 会话模型

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | String(UUID) | 网关生成，返回客户端 |
| `userId` | long | 来自 JWT（service 校验后透传结果） |
| `clientId` | String | 客户端自报设备标识，用于区分多端 |
| `transport` | enum | `POLL`（W4）/ `WS`（W6） |
| `lastDeliveredId` | long | **投递游标**，单调不减，初值 0 |
| `inbox` | Deque | POLL 下已拉未取的通知，容量 200 |
| `lastActiveAt` | Instant | 最近请求/心跳，用于 TTL |
| `state` | enum | `NEW` → `ACTIVE` → `CLOSED` |

存储：`ConcurrentHashMap<userId, ConcurrentHashMap<sessionId, Session>>`，两层是为了**按用户聚合拉取**（service 接口以 userId 为维度）。

生命周期：POLL 会话 5 分钟无请求回收；WS 会话 90 秒无心跳断开；同 `clientId` 重连**复用**旧会话；同用户第 4 个会话**踢掉最久未活跃**（返回 `SESSION_EVICTED`）。

## 3. 游标

定义：`cursor` = 该会话已成功投递的最大 `notification.id`。`id` **不连续**，禁止用 `cursor+1` 推导。

| 不变量 | 内容 | 破坏后果 |
|---|---|---|
| I1 | `cursor ≤ 该用户已提交的最大 id` | 游标超前 → 永久漏消息 |
| I2 | 已投递通知 `id ≤ cursor` | 重复投递 |
| I3 | 未投递通知 `id > cursor` | 漏投 |
| I4 | `cursor` 单调不减 | 重放历史 |

推进三原则：

1. **投递成功才推进**（POLL 写入 inbox、WS 的 `send` 返回成功）；
2. **一批只推进到本批最后一条成功投递的 id**（发送失败则停在前一条）；
3. **空页不推进**（service 无数据时原样返回入参 `afterId`，网关直接沿用）。

## 4. 投递顺序（自查项）

**结论**：同一用户按 `id` 升序投递；跨用户无顺序保证；语义为 **at-least-once**（重复由客户端按 id 去重）。

两个来源共同保证：

1. **服务端**：`NotificationDataServiceImpl.send()` 先 `credits.lockByUser(userId)` 锁用户信用行，锁持有到最外层事务提交，**之后**才插入并取自增 id → 同一用户的 id 分配顺序 = 提交顺序，不会出现“后一条先提交、游标跳过前一条”。
2. **网关侧**：按用户 `minCursor` 拉取 → 按各会话游标过滤 → 按返回顺序（`id ASC`）投递 → 成功后才推进。

**反例（为什么不按最大游标拉）**：若以最大游标为起点，落后最多的那个会话会永久漏掉 `(它的 cursor, max)` 之间全部消息。

## 5. 拉取算法

```java
// 每 2 秒一次；同一用户串行，不同用户并行
void pollUser(long userId) {
    long base = sessions(userId).min(Session::lastDeliveredId);
    long cursor = base;
    for (int round = 0; round < 5; round++) {           // 单 tick 最多 5 轮，防饿死
        Page p = service.notifications(userId, cursor, 50);
        if (p.items().isEmpty()) break;
        for (Session s : sessions(userId)) {
            List<Item> mine = p.items().filter(i -> i.id() > s.lastDeliveredId());
            if (deliver(s, mine)) advance(s, mine);      // 失败不推进，下轮重拉
        }
        cursor = p.lastId();
        if (!p.hasMore()) break;
    }
}
```

参数：每轮 `limit=50`（服务端上限 100）；单 tick 上限 5 轮 = 单用户 250 条/tick；周期 2 秒（手册 W4）。

## 6. 边界

| # | 边界 | 对策 |
|---|---|---|
| E1 | 多端并发 | 会话级游标 + 第 4 个踢最旧 |
| E2 | 客户端游标超前（重置数据后） | 网关记 `cursor_ahead` 日志；客户端支持重置游标；演示清单写明“重置数据后要重置客户端游标” |
| E3 | 鉴权失败/令牌过期 | 分层错误码：401 `UNAUTHORIZED`/`TOKEN_EXPIRED`、502 `UPSTREAM_FORBIDDEN`、503 `SERVICE_UNAVAILABLE`；**禁止把失败包成空列表** |
| E4 | 未知 `type` | 网关不解析，原样透传；客户端解析失败不得阻塞游标推进 |
| E5 | 假在线（半开连接） | 心跳 30s/判死 90s；连续 3 次投递失败即回收会话；重连加 0–2s 抖动 |
| E6 | 投递积压 | 单会话 inbox 200 封顶，满则停止推进并置 `overflow=true`（宁重复不丢失） |
| E7 | 版本错配 | 宽容解析未知字段；`items/lastId/hasMore` 缺失视为协议错误且不推进；部署顺序先 service 后 gateway |

## 7. 决策记录

| # | 决策 | 取值 | 理由 |
|---|---|---|---|
| D1 | W4 形态 | 后台拉取 + 客户端取 inbox（P2-lite） | W6 换 WS 只替换投递通道，拉取逻辑不重写 |
| D2 | 鉴权 | 透传 JWT 给 service 校验 | 网关不持有 `JWT_SECRET`，userId 无伪造风险 |
| D3 | 第 4 个会话 | 踢最旧 | 换机器不至于连不上 |
| D4 | 客户端不带 `afterId` | 返回 400 | 防止重放全量历史 |
| D5 | actuator 暴露面 | 仅 health + 版本信息 | 收敛攻击面 |

## 8. 验证计划（W4 落地时逐条执行）

- [ ] 单会话：发 3 条，游标 `0→3`，顺序正确
- [ ] 多端：两端游标独立，互不影响
- [ ] 断线：投递到第 2 条断开，重连带 `afterId=2` 只补第 3 条
- [ ] 空轮询：`lastId` 原样，游标不动
- [ ] 跨用户隔离：A 的消息不出现在 B
- [ ] 重启：会话清空后，客户端带 `afterId` 能续上
- [ ] 积压：250 条不取走 → `overflow=true`，游标停在 200 处

## 9. 待确认

| # | 事项 | 对象 |
|---|---|---|
| 1 | `INTERNAL_TOKEN` 四机统一分发方式（`.env` 由谁维护） | 成员 3 |
| 2 | 契约中 `type` 是否由 enum 改为 `string` + 文档枚举（E4 兼容性） | 成员 3（W2 评审） |
| 3 | 网关拉取 service 的超时/重试参数团队约定 | 成员 3 / 成员 5 |
| 4 | `reset-demo.ps1` 后客户端游标由谁重置 | 成员 4 / 成员 2 |

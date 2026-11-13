# 成员 3 PR 说明与验收自查

> 提 PR 时直接把下面「PR 描述」整段粘进 GitHub。

## PR 描述

**标题**：`feat(service): 成员3 服务端领域实现（契约/认证/预约/签到/批处理/管理端）`

**分支**：`feature/member3-service` → `main`

### 这个 PR 做了什么

按《团队执行手册》表 8（成员 3：架构 + 服务端领域）W1–W8 实现服务端业务层，
在成员 4 交付的持久化原语之上完成 5 件留白：认证、状态机、签到截止、信用规则、公共 API。

### 改动范围

| 类别 | 内容 |
|---|---|
| 契约 | `contracts/openapi.yaml`（20 路径 / 21 操作 / 40 schema），ADR 与架构说明 |
| 认证 | PBKDF2 密码哈希、HS256 JWT、拦截器、`CurrentUser` 注入、演示账号初始化 |
| 预约 | 创建（6 步校验 + 唯一索引裁决）、列表、详情、签到、取消 |
| 内部 | `expire-batch` / `complete-batch` / 通知增量读，`X-Internal-Token` 鉴权 |
| 通知 | 四个业务节点落库、游标增量、标记已读 |
| 管理端 | 座位状态、多条件查预约、强制取消、统计看板、信用查询与调整（调整需绑定预约） |
| 迁移 | `V3__checked_in_at.sql`（**未修改 V1**） |
| 文档 | ADR、一页架构、验证记录 |

### 数据层兼容性

- 未修改成员 4 任何已有方法签名与 SQL；只在其 Mapper 上**增量新增**只读查询
  （`findSeat`、`countActiveByUser`、`listByUserAndStatus`、`markCheckedIn`、候选扫描、管理端 JOIN）。
- 保留 `READ_COMMITTED`、生成列、唯一索引与通知锁顺序。
- `ReservationPO` 增加 `checkedInAt` 字段，对应 V3 新列。

### 需要 Review 关注的点

1. `V3` 迁移是否允许（手册要求"不改已执行迁移"，这里是新增版本，不是修改）。
2. 密码用 PBKDF2、JWT 手写 HS256，是为了**不新增第三方依赖**；若团队要求 BCrypt + jjwt 可替换。
3. **管理端信用调整采用方案 C**：必须绑定该用户的一条预约（受表 3 的 `reservation_id NOT NULL` 与 `uk_credit_res_reason` 约束）。代价：学生没有预约时无法调整；同一预约同一原因只能调一次。若评审认为必须支持"无预约也能改分"，需改为方案 B（`V4` 让 `reservation_id` 可空，动成员 4 的组件）。

### 自测情况

- `mvn -DskipTests package`：**BUILD SUCCESS**（common / service / gateway）
- 真实服务 + 真实 MySQL 实测 25+ 用例，见 `docs/成员3验证记录.md`
- 200 并发抢同座位：**1 成功 / 199 冲突**
- 本机 p95：座位图 6.4 ms、20 并发 44.3 ms（要求 < 300 ms）
- 管理端信用调整：加分/扣分/幂等/归属/越界/自动解除限制 全部实测
- `mvn test`：**尚未在提交者本机执行**（需评审后由提交者补跑并回帖）

---

## 6 条现场验收对照

| # | 手册 1.3 验收项 | 由谁执行 | 本次交付提供什么 | 证据 |
|---|---|---|---|---|
| 1 | 两学生抢同一座位，1 成功 1 "已被占用" | 成员 1 客户端 | `POST /reservations` 冲突返回 409 `SEAT_TAKEN` | 并发 200 线程 1/199；两账号实测 409 |
| 2 | 输座位码签到，状态变"使用中" | 成员 1 客户端 | `POST /{id}/check-in` → `CHECKED_IN` + `checkedInAt` | 实测 200 `CHECKED_IN`，重复签到时间不变 |
| 3 | 不签到到点释放并扣 10 分，只扣一次 | 成员 5 守护节点 | `expire-batch` 置 `EXPIRED` + `applyDelta(-10, NO_SHOW)` | 实测 `processed:1` → 再跑 `processed:0`，流水仅 1 条 |
| 4 | 停网关 2 分钟，预约与释放照常，重启补发 | 成员 6 网关 | 通知先落库 + `GET /internal/notifications?afterId=` | 网关可凭游标补拉；通知表有数据 |
| 5 | 停预约服务，守护不崩会重试，恢复补跑 | 成员 5 守护节点 | 批处理接口幂等，单条失败不影响同批 | 重复调用只扣一次；`failed` 记录下轮重扫 |
| 6 | 管理端停用座位、查预约、强制取消 | 成员 7 管理端 | `PATCH /admin/seats/{id}`、`GET /admin/reservations`、`POST .../force-cancel` | 均实测 200；重复强制取消 409 |

## 给其他成员的对接要点

**成员 1、2（学生端）**
- 登录后所有请求带 `Authorization: Bearer <token>`；401 `TOKEN_INVALID` 表示重新登录。
- `slotStart` 必须原样回传 `/slots` 返回的时间串（带 `+08:00`），不能自己拼。
- 409 `SEAT_TAKEN` 与 422 `LIMIT_EXCEEDED` 要给不同文案，前者是座位被抢，后者是自己预约太多。

**成员 6（网关）**
- **不要连 MySQL**，用 `GET /api/v1/internal/notifications?userId=&afterId=`，带 `X-Internal-Token`。
- 游标用返回的 `lastId`；`hasMore=true` 时立刻再拉一轮。

**成员 5（守护节点）**
- 每 30 秒调一次 `expire-batch` / `complete-batch`，body `{"limit":100}`。
- 超时或 5xx 直接重试，幂等已保证；`failed>0` 时下一轮会重新扫到。

**成员 7（管理端）**
- 所有 `/admin/**` 要求 `ADMIN` 令牌；学生令牌返回 403。
- 信用调整接口 `POST /api/v1/admin/credit/{userId}/adjust` 可用，但**必须传 `reservationId`**（该用户的一条预约），流水会挂在预约上。
- 已知边界：学生没有预约时无法改分；同一预约 + 同一原因码只能调一次，二次调整要换 `reason`。

## 提交前自查

- [x] 只改了服务端与文档，没碰客户端/守护/网关代码
- [x] 没有提交 `.env`、`.build/`、`target/`
- [x] `V1` 未修改，结构变更走 `V3`
- [x] 提交信息符合 `feat|fix|docs(scope): 说明`
- [ ] `mvn test` 通过（提交者本机补跑）
- [ ] 已推送分支并开 PR（待执行）

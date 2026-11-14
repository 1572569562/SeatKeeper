# SeatKeeper 管理端（seatkeeper-admin）

图书馆 / 自习室座位预约系统的管理端，JavaFX 21 桌面程序。
这是**成员 7** 的工程，对应手册第 6 章「成员 7：管理端」。

## 现在能干什么

| 能力 | 状态 |
|---|---|
| 管理员登录（本地模拟，不依赖服务端） | ✅ 已完成（W1） |
| 学生账号登录被拒 | ✅ 已完成（对应验收和 W7「越权被拦住」） |
| 主界面外壳（左侧菜单 + 内容区） | ✅ 已完成 |
| **座位管理：楼层/阅览室切换 + 座位网格 + 点座位改状态** | ✅ 已完成（W2 假数据） |
| **座位管理接真接口**（`GET/PATCH /api/v1/admin/seats`） | ✅ 已完成（W3） |
| **预约查询：学号/座位/状态/日期四条件 + 分页** | ✅ 已完成（W4，已接 `GET /api/v1/admin/reservations`） |
| **强制取消（必须填原因）** | ✅ 已完成（W5，`POST .../force-cancel`） |
| **信用管理：按用户查快照 + 带原因调整** | ✅ 已完成（W5，`GET /admin/credit/{userId}` + `POST .../adjust`） |
| **统计看板：预约数 / 已签到 / 已过期 / 签到率 / 爽约率** | ✅ 已完成（W6，已接 `GET /api/v1/admin/stats`） |
| 爽约率的数据来源（守护节点置 EXPIRED） | ⏳ **等成员5**，见文末「待补依赖」 |
| 调真实服务端接口 | ✅ 登录 + 座位都已接，改 `app.auth.mode=http` 切换 |

## 怎么跑

```bash
cd seatkeeper-admin
mvn javafx:run
```

第一次跑会下载依赖（150 MB 左右，走阿里云镜像，几分钟）。之后是秒开。

**演示账号**（本地模拟模式）：

| 账号 | 密码 | 结果 |
|---|---|---|
| `admin` | `admin123` | 能进主界面 |
| `20230101` | `123456` | 提示「该账号不是管理员，无法登录管理端」 |
| `admin` | 随便输错 | 提示「账号或密码错误」 |

## 怎么连真实服务端

等 Pair B 的预约服务起来之后（默认在 A 机 8080 端口），改
`src/main/resources/application.properties`：

```properties
app.server.baseUrl=http://192.168.1.21:8080
app.auth.mode=http
```

或者不改文件，启动时直接传参：

```bash
mvn javafx:run -Dapp.server.baseUrl=http://192.168.1.21:8080 -Dapp.auth.mode=http
```

调的是 `POST /api/v1/auth/login`，契约见仓库 `contracts/openapi.yaml`。
服务端返回的 `role` 不是 `ADMIN` 时，客户端一样会拦住——**权限校验前后端都要做，不能只靠隐藏按钮**。

## 目录说明

```
seatkeeper-admin/
├─ pom.xml                      Maven 配置（JavaFX 21 + Jackson）
└─ src/main/
   ├─ java/com/seatkeeper/admin/
   │  ├─ App.java               入口，负责页面切换
   │  ├─ config/AppConfig.java  配置读取（参数 > 环境变量 > 配置文件）
   │  ├─ api/AuthService.java   登录接口（界面只认这个接口）
   │  ├─ api/MockAuthService.java  本地模拟实现
   │  ├─ api/HttpAuthService.java  真实 HTTP 实现
   │  ├─ api/ApiEnvelope.java   {code,message,data} 信封 + 错误码翻译
   │  ├─ api/ApiException.java  带错误码的异常
   │  ├─ api/LoginResult.java   登录结果
   │  ├─ session/Session.java   token / 角色 / 姓名 / 信用分
   │  ├─ domain/Seat.java       座位（对应契约 SeatVO）
   │  ├─ domain/SeatStatus.java 可用 / 停用（契约只有这两态）
   │  ├─ domain/AdminReservation.java 管理视角预约（对应 AdminReservationVO）
   │  ├─ domain/ReservationStatus.java 五种状态（RESERVED…COMPLETED）
   │  ├─ domain/ReservationFilter.java 查询条件
   │  ├─ domain/PageResult.java 分页结果 {items, page, size, hasMore}
   │  ├─ domain/Credit.java     信用快照（对应 CreditVO）
   │  ├─ repo/SeatRepository.java    座位数据来源（接口）
   │  ├─ repo/MockSeatRepository.java 本地假数据：2 层 × 2 区 × 12 座
   │  ├─ repo/HttpSeatRepository.java 调 /api/v1/admin/seats
   │  ├─ repo/ReservationRepository.java    预约数据来源（接口，含强制取消）
   │  ├─ repo/MockReservationRepository.java 本地假数据：约 60 条预约
   │  ├─ repo/HttpReservationRepository.java 调 /api/v1/admin/reservations
   │  ├─ repo/CreditRepository.java    信用数据来源（接口）
   │  ├─ repo/MockCreditRepository.java 本地假信用（默认 100 分）
   │  ├─ repo/HttpCreditRepository.java 调 /api/v1/admin/credit/*
   │  ├─ ui/AdminDialogs.java   强制取消 / 信用调整两个弹窗（都要求填原因）
   │  ├─ ui/Async.java          阻塞调用丢后台线程，结果回 FX 线程
   │  ├─ ui/LoginView.java      登录页
   │  ├─ ui/MainView.java       主界面外壳（左侧导航 + 内容卡片）
   │  ├─ ui/SeatManageView.java 座位管理页（W2 交互 + W3 接口）
   │  ├─ ui/ReservationQueryView.java 预约查询页（W4 查询 + W5 操作）
   │  ├─ ui/CreditView.java     信用管理页（W5）
   │  └─ ui/StatsView.java      统计看板页（W6）
   ├─ resources/application.properties  服务端地址 + 运行模式
   └─ resources/admin.css              全局样式（颜色、导航、卡片、座位格、表格、状态标签）

## 管理端对接的接口（来自团队契约）

| 接口 | 用途 | 周次 | 状态 |
|---|---|---|---|
| `POST /api/v1/auth/login` | 登录拿 JWT | W1 | ✅ 已接 |
| `GET /api/v1/admin/seats?room&floor&status` | 座位列表 | W3 | ✅ 已接 |
| `PATCH /api/v1/admin/seats/{id}` | 停用 / 启用座位 | W3 | ✅ 已接 |
| `GET /api/v1/admin/reservations` | 预约查询（多条件 + 分页） | W4 | ✅ 已接 |
| `POST /api/v1/admin/reservations/{id}/force-cancel` | 强制取消 | W5 | ✅ 已接 |
| `GET /api/v1/admin/credit/{userId}` | 查信用 | W5 | ✅ 已接 |
| `POST /api/v1/admin/credit/{userId}/adjust` | 调整信用 | W5 | ✅ 已接 |
| `GET /api/v1/admin/stats?from&to` | 统计看板 | W6 | ✅ 已接 |

**已知契约缺口（已反馈成员3）**：`AdminReservationVO` 没有 `userId` 字段，
而信用接口是 `/admin/credit/{userId}`，所以从预约行调信用调整时拿不到用户 ID。
客户端现在把 `userId` 解析成可空字段，缺失时弹窗里手动填，等 VO 补上字段后自动带出。

**所有响应都套一层信封**，成功时数据在 `data` 里：

```json
{"code":"OK","message":"操作成功","data":{...}}
{"code":"FORBIDDEN","message":"没有权限","data":null}
```

`ApiEnvelope` 负责拆信封并把错误码翻译成中文，两个 Http 实现共用。

**注意**：座位是预置数据，契约里**没有新增/删除座位的接口**，管理端只能查列表和改状态。

**界面约定**（后面加页面请沿用）：

- 窗口底色 `#f5f7fa`，所有内容放进白色 `.card`
- 左侧导航用 `ToggleButton` + `.nav-item` 样式，**不要用 ListView**（默认斑马纹看着像表格）
- 选中态：左侧 3px 蓝条 + 浅蓝底 + 蓝色文字
- 顶栏左边是面包屑（`管理端 / 当前页`），不再重复显示品牌名
- 颜色：品牌蓝 `#2563eb`，边框 `#e5e7eb`，次要文字 `#6b7280`

**为什么把登录拆成接口**：`MockAuthService` 和 `HttpAuthService` 是同一个接口的两个实现，
界面代码一行都不用改。服务端没好之前用模拟的，好了就切真的，这是手册 W2「客户端能调 mock」那条的落地方式。

## 关于 JavaFX 依赖（Eclipse 用户必读）

`pom.xml` 里 JavaFX 是**三个包、带平台分类符、并排除了各自的自我依赖**，看着啰嗦，但必须这样写。

原因：`javafx-controls` 的官方 POM 里，依赖它自己时用的是 `<classifier>${javafx.platform}</classifier>`，
而这个值只能由「按操作系统激活的 profile」提供。命令行 Maven 会执行这类 profile，
但 Eclipse 的 m2e 在解析依赖时不执行，占位符原样保留，于是报：

```
Could not find artifact org.openjfx:javafx-controls:jar:${javafx.platform}:21.0.2
```

现在平台值写死在 `pom.xml` 的 `<javafx.platform>` 属性里（当前 `win`）。
**换到 macOS / Linux 只需改这一处**（`mac` / `linux`）。

## 测试

```bash
mvn test
```

覆盖：登录模拟（4）、响应信封（6）、配置（3）、座位假数据（7）、
预约假数据（13）、统计口径（9）、真接口集成（26），共 68 个。

## 待补依赖（W6 的「预定着」部分）

统计看板里有两个数**依赖别人的活**，不是客户端能补的：

| 数据 | 依赖 | 现状 |
|---|---|---|
| `noShow`（已过期）| **成员5 守护节点**每 30 秒扫一次，把超期未签到的预约置成 `EXPIRED` | 守护节点没有任何提交，也没有分支 |
| 对应地 `noShowRate`（爽约率）| 同上 | 守护节点没跑时这个值为 0，页面已写明原因 |

**已经做好的部分**：接口对接、口径计算、界面展示、日期区间筛选、空区间处理、
mock 数据与「预约查询」页数字一致。

**等就位后要做的**：不用改代码。守护节点开始推进状态、服务端 `noShow` 有值，
看板上的爽约率自然就准了。唯一要验证的是「看板数字 = 预约查询里 EXPIRED 的条数」。

## 下一步（对照手册成员 7 的表）

- **W1**：✅ 管理端骨架 + 管理员登录
- **W2**：✅ 座位列表 + 编辑弹窗原型（假数据，能改状态并看到变化）
- **W3**：✅ 座位管理接接口（`GET/PATCH /api/v1/admin/seats`）
- **W4**：✅ 预约查询（学号 / 座位 / 状态 / 日期四条件 + 分页）
- **W5**：✅ 强制取消（填原因）、信用调整（带原因、绑定归属预约）
- **W6**：✅ 统计看板（预约数 / 签到率 / 爽约率）—— 爽约率的**数据来源**等成员5 守护节点
- **W7**：权限校验真机验证（服务端 `AuthInterceptor` 已对 `/api/v1/admin/**` 强制 ADMIN）
- **W8**：界面打磨、管理 API 契约测试

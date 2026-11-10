# SeatKeeper 第14组：成员4 数据与平台

本工程实现团队执行手册中“成员4：数据与平台”的W1—W8工作，提供可供成员3接入的数据库和持久化组件。已经完成的代码、验证情况和后续团队联调分别记录如下。团队仓库为 [1572569562/SeatKeeper](https://github.com/1572569562/SeatKeeper)，成员4工作分支为 `feature/member4-data-platform`。

当前工程按照个人规范整理Java分层，保留团队手册规定的common/service两个Maven模块。独立复用模板保留在本地同级目录，不包含在本次团队仓库提交中；模板与正式工程使用不同数据库，不能混用Flyway历史。

为独立验证数据组件，本工程随附启动骨架、基础响应与异常处理、健康接口。这些基础件供成员3整合；正式架构决策、统一接口协议和错误码由成员3统一。成员4的交接重点是DDL、Mapper、持久化组件、数据一致性测试和运维脚本。

## 逐周完成清单

| 周次 | 已完成的代码与文件 | 验证状态 |
| --- | --- | --- |
| W1 | MySQL Docker Compose、Hikari配置、Flyway启动迁移、数据源健康检查 | 工程编译/单测通过；真实MySQL迁移与连接池已测；Docker MySQL8.0.44容器健康，容器迁移/连接池实测通过 |
| W2 | 7张业务表、model.po 中的 Java record 持久化对象、slot时段、生成列、唯一索引、外键、演示种子 | 空库迁移与重启校验通过；种子为3用户、8座位、7时段 |
| W3 | 用户/座位/时段查询与MyBatis映射、初版索引 | 实际Mapper的room+floor查询使用索引；无条件读小表可能全表扫描，详见EXPLAIN记录 |
| W4 | 预约INSERT SELECT、活动唯一约束、version条件更新、事务组件 | 200并发抢同座位为1成功199冲突；同用户同槽互斥；旧版本更新失败；取消/过期可重订 |
| W5 | credit/credit_log持久化、行锁、流水幂等、余额校验、归属核验 | 100次同请求只1条流水；越界与外层失败完整回滚；不同用户并发写入回归 |
| W6 | notification持久化、按用户分页/游标补拉、标记已读、有界清理 | 用户隔离、游标顺序与并发提交回归；同用户发送锁到事务提交避免漏通知 |
| W7 | MySQL备份/恢复、6种EXPLAIN场景、索引检查工具 | 中文备份恢复一致；原样Mapper查询计划已记录；四机与大数据p95压测待团队完成 |
| W8 | 固定日期演示重置、DDL说明、数据字典、组件交接文档 | 两次重置七表数据完全一致，Flyway历史逐字段保持不变 |

- [x] 代码、SQL、XML和文档使用UTF-8；代码注释中文；PowerShell脚本UTF-8 BOM。
- [x] 真实MySQL验证，不用H2替代生成列、锁和事务。
- [x] 独立审查发现的并发、测试库保护和模板迁移冲突已补回归并修复。
- [x] 正常本机权限启动Docker后，独立MySQL8.0.44容器实启、Flyway迁移、初版20项测试及容器备份恢复均通过；包结构调整后的33项回归结果见验证记录。
- [ ] 与成员3确认字段、7个时段、状态机、扣分/限制规则及接口错误码。
- [ ] 按全组计划完成四机联通、故障演练及真实数据量压测。这些不等同于数据组件单独测试。

## 代码阅读与中文注释

每个手写Java方法和构造器均有中文说明，包括实现类、Mapper和测试方法；信用行锁、幂等、版本条件与通知提交顺序等关键位置补充了原因说明。运维共用函数也有中文注释。

七张表逐表、逐字段、逐索引和逐约束的说明见 [建表语句中文注释版](docs/建表语句_中文注释.sql)。该文件是原始建表SQL的阅读副本，仅增加注释，不参与启动迁移；实际运行继续使用原始V1，保持已经执行的Flyway校验和。

## 工程目录

```text
seatkeeper/
├─ pom.xml                         Maven聚合工程
├─ seatkeeper-common/
│  └─ src/main/java/cn/edu/stu/seatkeeper/model/result/  Result、ResultUtil
├─ seatkeeper-service/
│  ├─ src/main/java/cn/edu/stu/seatkeeper/
│  │  ├─ SeatkeeperServiceApplication.java
│  │  ├─ api/                     HealthController：仅调用服务并返回视图
│  │  ├─ config/                  MybatisConfig
│  │  ├─ exception/               通用错误、业务异常和全局处理
│  │  ├─ mapper/                  四个MyBatis持久化接口
│  │  ├─ model/
│  │  │  ├─ dto/                  SeatQueryDTO
│  │  │  ├─ po/                   七个持久化对象，后缀PO
│  │  │  ├─ vo/                   HealthVO
│  │  │  └─ enums/                ReservationStatus
│  │  ├─ service/                 Health及四个DataService接口
│  │  │  └─ impl/                 对应ServiceImpl，事务与数据库操作
│  │  └─ utils/                   ParameterCheckUtil
│  ├─ src/main/resources/mapper/  MyBatis XML
│  ├─ src/main/resources/db/migration/  V1七表结构
│  ├─ src/main/resources/db/demo/  V2可选演示数据
│  └─ src/test/                   19项单测和14项真实MySQL集成测试
├─ deploy/compose.mysql.yml        MySQL8.0，仅绑定本机
├─ scripts/                       启动、备份、恢复、重置及EXPLAIN
├─ docs/                          数据字典、DDL、交接与验证记录
└─ .env.example                   配置样例，不包含真实密码
```

当前只实现common和service两个模块。JavaFX、守护节点、通知网关、完整登录/JWT及预约公共接口由对应成员实现。健康接口为 `GET /api/v1/health`，Actuator为 `GET /actuator/health`；没有匿名数据写入API。

分层和命名参照 [java-springboot-backend-standards](https://github.com/beemines/java-springboot-backend-standards)：Controller → Service接口 → ServiceImpl → Mapper；DTO接收参数、PO对应数据库、VO对外返回。Result/ResultUtil位于common模块，响应字段保持 `code/message/data`，成功码为 `OK`。

## 环境与启动

项目数据库统一命名为 `seatkeeper`。`demo` 配置仅表示加载演示数据，不再用作数据库名称后缀。

团队基线是JDK17、Maven3.9、MySQL8.0、Docker/Compose v2。本机测试实际使用Oracle JDK17.0.17、Maven3.9.4、独立MySQL8.0.34与Docker MySQL8.0.44；团队要求的Temurin17发行版需组内环境验收。本地开发端口3307避免与现有MySQL冲突；按手册部署机器A时将MYSQL_PORT和DB_URL改为3306。MySQL仍仅绑定127.0.0.1，其他机器访问预约服务而非直接写数据库。

在项目根目录使用PowerShell7.2及以上版本：

```powershell
$env:JAVA_HOME='你的JDK17目录'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
java -version
mvn -v
Copy-Item .env.example .env
```

编辑 `.env`，替换两处密码占位值，保持MYSQL_PASSWORD与DB_PASSWORD一致。若没有本机mysql工具，可删除MYSQL_BIN或改为自己的目录。`.env`已经被Git忽略。

```powershell
docker compose --env-file .env -f deploy/compose.mysql.yml up -d mysql
docker compose --env-file .env -f deploy/compose.mysql.yml ps
mvn clean verify
./scripts/start-service.ps1 -JavaHome $env:JAVA_HOME -Demo
```

脚本会显式加载 `.env` 的字面量，已存在的环境变量优先。Spring Boot本身不会自动读取dotenv。直接 `java -jar` 时请自己设置DB_URL、DB_USER、DB_PASSWORD；例如使用已有MySQL时：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3307/seatkeeper?serverTimezone=Asia/Shanghai&characterEncoding=UTF-8'
$env:DB_USER='seatkeeper'
$env:DB_PASSWORD='本机实际密码'
java -jar seatkeeper-service/target/seatkeeper-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=demo
```

数据库需预先创建，账号需有该库建表权限。运行demo profile仅限专用演示库，它附加V2演示种子；默认profile只执行V1表结构。Flyway使用独立的flyway_schema_history元数据表，不计入7张业务表。禁止修改已执行的版本脚本，后续结构调整新增V3等迁移。Flyway clean已禁用。

访问 `http://127.0.0.1:8080/api/v1/health` 应返回code=OK、database=UP。启动日志中应出现HikariPool启动和Flyway验证/迁移记录。demo用户password_hash为明确禁用登录的标记，不提供可登录账号；认证由成员3接入。

四机部署时，预约服务需要绑定机器A的内网地址（SERVER_ADDRESS），并由团队配置内网访问；MySQL仍保持仅本机可访问。通知网关在另一台机器而数据库仅监听本机，因此需要与成员3、6确认内部只读通知接口的接入方式。不要通过共享数据库写账号解决跨机通信问题。

## 数据一致性约定

7张表为app_user、seat、slot、reservation、credit、credit_log、notification。Java使用Asia/Shanghai本地时间，数据库DATETIME(6)、会话+08:00。

- 活动预约使用生成列和两组唯一索引，不能依赖先查空闲再插入。状态结束后生成列为NULL，可重新预约。
- 时段开始/结束必须对应slot中的固定组合；创建预约从slot选出结束时间。示例7个两小时时段是实施默认值，需与成员3确认。
- Hikari显式采用READ_COMMITTED隔离级别；信用主键行锁配合流水唯一约束，避免缺键查询的RR间隙锁死锁。成员3接入时不要擅自覆盖隔离级别。
- 通知发送也锁住同一用户的credit行直到事务提交，避免ID先分配后提交乱序导致afterId漏消息。新建用户必须同事务建立初始信用账户；信用和通知按一致顺序加锁。
- 多表业务操作由成员3的外层公开方法添加@Transactional；数据组件自动加入同一事务。完整状态机、认证、签到截止、信用限制策略不在数据层代判。

公开Java方法、错误码、分页边界和事务用法见 [数据层交接](docs/数据层交接.md)。DDL与索引见 [DDL说明](docs/DDL说明.md) 和 [数据字典](docs/数据字典.md)。

## 测试与运维

`mvn clean verify`执行无需数据库的普通单测。真实MySQL集成测试单独执行，目标数据库名称必须严格以 `_test` 结尾；测试会清空其7张业务表，不可使用共享库：

```powershell
$env:SEATKEEPER_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/seatkeeper_test?serverTimezone=Asia/Shanghai'
$env:SEATKEEPER_TEST_DB_USER='你的测试库账号'
$env:SEATKEEPER_TEST_DB_PASSWORD='你的测试库密码'
mvn -Pintegration verify
```

请先创建seatkeeper_test并授权测试账号。本机验收用了独立33416实例，账号和空密码只适用于该隔离测试实例，不是部署默认值。最终执行结果见 [验证记录](docs/验证记录.md)。

备份、恢复、重置、EXPLAIN支持Native本机工具和Docker容器两种模式，具体命令见 [运维说明](docs/运维说明.md)。恢复与重置只接受项目库 `seatkeeper` 或以 `_test` 结尾的测试库，且要求显式确认参数；重置不会删除迁移历史。EXPLAIN真实查询计划及限制见 [查询计划记录](docs/EXPLAIN检查.md)。

成员3接手顺序：确认DDL/模型与业务规则 → 注入service接口 → 在外层事务串接预约/信用/通知 → 按contracts确定公共API → 与客户端、守护、网关联调。内部实现计划保存在docs/design与docs/plans，README中的待验事项不会被计作已验收。

本轮用于验收的JAR、独立本机MySQL和专用Docker容器在验证后停止；数据目录与Docker卷保留。项目不会修改你的全局JAVA_HOME或原有数据库，后续按上述步骤启动自己的开发环境。

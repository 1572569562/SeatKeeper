# EXPLAIN 检查（成员4 W7）

工具为 `scripts/explain.ps1`。在已完成 Flyway 和 demo 种子的专用库执行：

```powershell
./scripts/explain.ps1 -Database seatkeeper -OutputFile ./backups/explain.txt
# 此次隔离本地验证实例使用 -Port 33416 -User root
```

脚本只执行版本/时间查询与六组 EXPLAIN，不写入业务数据。其中两组原样对应 `CatalogMapper.xml` 的 `listSeats`：room+floor双条件和无条件全量；另四组是运维场景。可使用 `-UserId`、`-ReservationId`、`-LastNotificationId` 改变查找值；固定槽时间对应 `reset-demo.ps1 -DemoDate 2030-01-15`。输出记录 MySQL 版本、库名和检查时间，已有输出文件会拒绝覆盖。

| 检查项 | 查询条件 | 预期访问路径 | 核验重点 |
| --- | --- | --- | --- |
| W3 Mapper座位双条件 | 只有room、floor；按room,floor,seat_no,id排序，无LIMIT | uk_seat_location(room,floor,seat_no) | 原样匹配CatalogMapper.listSeats的过滤与排序 |
| W3 Mapper全量座位 | 无WHERE；按room,floor,seat_no,id排序，无LIMIT | 无过滤，需要返回全部行 | 小表ALL扫描是否符合全量读取场景 |
| 扩展座位状态场景 | room、floor、status，按id排序，限50；不是Mapper原SQL | idx_seat_room_floor_status(room,floor,status,id) | 是否使用筛选前缀，能否避免额外排序 |
| 活动预约 | active_seat_id + slot_start | uk_reservation_active_seat(active_seat_id,slot_start) | 生成列上的等值查找，避免扫描预约全表 |
| 通知游标 | user_id + id > cursor，按id升序限50 | idx_notification_user_id(user_id,id) | 用户隔离，游标范围访问 |
| 信用幂等 | reservation_id + reason | uk_credit_log_reservation_reason(reservation_id,reason) | 唯一记录查找，便于幂等事务读取 |

小规模演示库的优化器可能选择全表扫描，不能仅凭 `possible_keys` 认定已实际使用索引。记录真实 `type`、`key`、`rows`、`Extra`，必要时在独立测试库生成接近目标规模的数据后再比较。脚本不使用 FORCE INDEX，因此输出反映优化器实际选择。

以下为改名前的历史查询计划记录，保留当时的库名；当前运行使用 `seatkeeper`。

## Docker MySQL 8.0.44：2026-11-09 13:03实测

本机最初的默认沙箱检查无法访问Docker命名管道；正常本机权限启动已有Docker Desktop后，实际项目 `seatkeeper-member4-check` 的 `mysql:8.0.44` 容器healthy。宿主回环33417映射容器3306；本轮使用 `-Mode Docker` 和应用账号 `seatkeeper`，只读查询本任务 `seatkeeper_demo`。此前容器模式的两次重置及中文备份恢复也已真实通过，详见 [运维说明.md](运维说明.md)。

```powershell
pwsh -NoProfile -File ./scripts/explain.ps1 -Mode Docker -Database seatkeeper_demo -EnvFile .runtime/docker-verification.env -User seatkeeper -OutputFile ./scripts/tests/artifacts/20261109-050231-7baff194/explain.txt
```

实际MySQL版本8.0.44，检查时间 `2026-11-09 13:03:13.308653`（+08:00），退出码0。六组计划全部来自同一轮原始输出，未使用FORCE INDEX或为此修改统计信息；`rows`仍是估算值。

| 查询 | type | possible_keys | 实际key | key_len | rows估算 | Extra |
| --- | --- | --- | --- | --- | --- | --- |
| catalog_seats_room_floor | ref | uk_seat_location, idx_seat_room_floor_status | uk_seat_location | 262 | 4 | NULL |
| catalog_seats_all | ALL | NULL | NULL | NULL | 1 | Using filesort |
| seat_filter | index | uk_seat_location, idx_seat_room_floor_status | PRIMARY | 8 | 1 | Using where |
| active_reservation | const | uk_reservation_active_seat, idx_reservation_slot_interval | uk_reservation_active_seat | 17 | 1 | NULL |
| notification_cursor | range | PRIMARY, idx_notification_user_id | idx_notification_user_id | 16 | 1 | Using index condition |
| credit_idempotency | const | uk_credit_log_reservation_reason | uk_credit_log_reservation_reason | 266 | 1 | NULL |

本轮真实COUNT核对座位有8行，不能把全量查询的 `rows=1` 写成表中只有1行或实测只扫描1行。无条件查询需要返回全部数据，小表ALL扫描并排序属于合理选择；room+floor原样查询则实际使用位置索引且无额外排序。扩展状态筛选本轮选择主键索引扫描，保留WHERE检查，与Native首轮的位置索引加排序计划不同；本页分别保留原始结果，不据此认定专用筛选索引已命中或大数据性能达标。

完整输出及容器状态证据保存在新目录 `scripts/tests/artifacts/20261109-050231-7baff194/`。本次已验证Docker真实运行和六组查询计划，尚未执行四机、p95或大规模吞吐量压测。

## Native W3 Mapper 原样查询：2026-11-09 12:17实测

先核对 `seatkeeper-service/src/main/resources/mapper/CatalogMapper.xml` 的 `listSeats`，其条件只有可选的room、floor；没有status和LIMIT，排序为 `room, floor, seat_no, id`。脚本已加入以下原样查询，测试 `scripts/tests/test-explain-mapper.ps1` 从Mapper文件读取条件和排序并与脚本核对，防止把扩展场景当作W3原SQL验收。

```sql
EXPLAIN SELECT id, room, floor, seat_no, status FROM seat
WHERE room = '第一阅览室' AND floor = 1
ORDER BY room, floor, seat_no, id;

EXPLAIN SELECT id, room, floor, seat_no, status FROM seat
ORDER BY room, floor, seat_no, id;
```

在同一隔离MySQL 8.0.34、`seatkeeper_demo`（8行座位）只读执行，检查时间 `2026-11-09 12:17:13.599641`（+08:00），退出码0：

| 查询 | type | possible_keys | 实际key | key_len | rows估算 | Extra |
| --- | --- | --- | --- | --- | --- | --- |
| catalog_seats_room_floor | ref | uk_seat_location, idx_seat_room_floor_status | uk_seat_location | 262 | 4 | NULL |
| catalog_seats_all | ALL | NULL | NULL | NULL | 8 | Using filesort |

room+floor双条件确实使用了位置索引，且本次没有额外排序。无条件查询本来就要返回全部8行，小表全表扫描并排序是合理的优化器选择，不能仅凭ALL认定查询失败，也不能把NULL key理解为索引不存在。两项结果仅验证此演示规模下的真实计划；大数据的延迟、吞吐量与排序开销仍待测量。

真实命令和完整六组原始输出：

```powershell
pwsh -NoProfile -File ./scripts/explain.ps1 -Database seatkeeper_demo -Port 33416 -User root -MysqlBin D:/develop/mysql-8.0.34-winx64/bin -OutputFile ./scripts/tests/artifacts/20261109-040638-da536e22/explain-catalog-review.txt
```

## Native 首轮运维场景：2026-11-09 12:07实测

检查环境：独立 `127.0.0.1:33416`，MySQL Community 8.0.34，库 `seatkeeper_demo`，检查时间 `2026-11-09 12:07:25.241378`（会话+08:00）。已完成最终V1/V2 Flyway迁移与两次固定日期重置；7张业务表共有26行，分别为3/8/7/2/3/1/2行。实际命令：

```powershell
pwsh -NoProfile -File ./scripts/explain.ps1 -Database seatkeeper_demo -Port 33416 -User root -MysqlBin D:/develop/mysql-8.0.34-winx64/bin -OutputFile ./scripts/tests/artifacts/20261109-040638-da536e22/explain.txt
```

命令退出码0；未使用 FORCE INDEX，结果如下。`rows`为优化器估算值，不是实测扫描行数或耗时。

| 查询 | type | possible_keys | 实际key | key_len | rows估算 | Extra |
| --- | --- | --- | --- | --- | --- | --- |
| seat_filter | ref | uk_seat_location, idx_seat_room_floor_status | uk_seat_location | 262 | 4 | Using where; Using filesort |
| active_reservation | const | uk_reservation_active_seat, idx_reservation_slot_interval | uk_reservation_active_seat | 17 | 1 | NULL |
| notification_cursor | range | PRIMARY, idx_notification_user_id | idx_notification_user_id | 16 | 1 | Using index condition |
| credit_idempotency | const | uk_credit_log_reservation_reason | uk_credit_log_reservation_reason | 266 | 1 | NULL |

活动预约、通知游标、信用幂等三组查询实际使用了预期索引。座位筛选的专用复合索引虽然列在 `possible_keys`，此次8行座位小表实际选择了 `uk_seat_location`，并有额外排序；不能把它写成专用索引已命中或排序已消除。该查询在接近目标数据量时仍需重新测量计划、排序开销和响应延迟，再决定是否需要调整；本次不为得到预期展示结果而强制索引或修改结构。

首轮四组原始完整表格输出位于本地 `scripts/tests/artifacts/20261109-040638-da536e22/explain.txt`；补充W3原样查询后的六组输出为同目录 `explain-catalog-review.txt`。证据目录不进入版本库，本文保留实际计划以便交接复现。原 `seat_filter` 是扩展状态筛选场景，与W3 Mapper查询的验收结论分别记录。

EXPLAIN 只说明计划，不能替代延迟、吞吐量与并发正确性测试。本次Native与Docker均已取得真实计划，但没有执行大数据压测，不宣称性能达标。200并发座位竞争与信用幂等测试由持久层测试单独记录，不能由本页推导；四机通信及p95目标仍待验收。

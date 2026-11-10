# DDL 与约束说明

逐字段、逐索引和约束的SQL解释见 [建表语句中文注释版](建表语句_中文注释.sql)。该阅读版只增加中文注释，正式启动仍使用已执行且保持原文的V1迁移。

入口：`seatkeeper-service/src/main/resources/db/migration/V1__schema.sql`。使用 MySQL 8.0.16+ 以确保 CHECK 真正生效，实际验收使用 MySQL 8.0。不要将 H2 测试结果用于判断这些约束。

七张业务表按依赖顺序为 app_user、seat、slot、reservation、credit、credit_log、notification。Flyway 默认只扫描 `classpath:db/migration`；demo 才附加 `classpath:db/demo`。V1 不创建用户或演示预约。V2 演示数据脚本采用固定主键和幂等插入，可由受保护的演示重置流程重复执行。

预约存在两条活动唯一索引：`(active_seat_id, slot_start)` 和 `(active_user_id, slot_start)`。生成列只对 RESERVED、CHECKED_IN 保留标识，对 CANCELLED、EXPIRED、COMPLETED 返回 NULL。MySQL 唯一索引允许多条 NULL，因此取消或过期立即释放该时段的座位和用户占用；冲突由数据库裁决，不能通过“先查空闲再插入”替代。

`reservation.slot_start` 引用 `slot.start_time`，并通过 `(slot_start,slot_end)` 组合外键引用 slot 的同一时间区间。Mapper 使用 `INSERT ... SELECT` 从 slot 获取结束时间，不接受调用者传入 slot_end。组合外键也阻止直接 SQL 伪造结束时间。该方案以固定时段为前提，不提供任意相交区间排他功能。

信用 score、balance_after 使用 0..100 检查约束。事务组件先确认预约所属用户，再锁定该用户的 credit 行，然后读取 `(reservation_id,reason)` 流水；已有流水必须 user_id、delta 一致才返回同一结果。新请求在锁内计算余额并更新 credit、插入流水，任一步异常都回滚。唯一键是第二道防线，行锁保证同一用户信用修改串行化。reason 区分大小写；调用方应统一使用稳定原因码。

连接池必须使用 READ COMMITTED（Hikari 的 `TRANSACTION_READ_COMMITTED`），外层业务事务保持相同隔离级别。否则 REPEATABLE READ 下对不同缺失幂等键的 `FOR UPDATE` 可能同时持有间隙锁，随后不同用户各自首次插流水也会死锁。该要求通过真实双事务闸门回归和实际会话隔离级别断言验证。

`reservation.version`、`credit.version` 更新均包含旧版本条件并执行 `version=version+1`。更新行数为 0 表示记录不存在、归属不符或版本冲突，调用方需重新读取再做业务决策。

通知索引 `(user_id,id)` 支持按用户倒序分页与按 id 正序增量查询；`(read_flag,created_at,id)` 支持只清理已读且严格早于保留期的数据。清理接口每次最多 1000 条，调用者按调度需求重复调用。未读消息和恰好处于截止时间的消息不会被删除。

通知自增 ID 本身不保证提交顺序。发送组件在插入前锁定同用户已有的 credit 主键行，并将锁保持到最外层事务结束，因此同用户后一次发送不会先分配较大 ID 再提前提交。用户开户必须同步创建 credit，所有通知写入必须经过该组件。锁顺序固定为先信用、后通知，不升级 app_user 外键共享锁。

业务外键采用 RESTRICT，不级联删除。所有表为 InnoDB；UTF-8 为 utf8mb4；时间为 DATETIME(6)，连接会话时区 `+08:00`。这些约束必须在同版本真实 MySQL 上验证后再交接。

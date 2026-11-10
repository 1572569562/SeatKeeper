package cn.edu.stu.seatkeeper.integration;

import cn.edu.stu.seatkeeper.mapper.CreditMapper;
import cn.edu.stu.seatkeeper.model.po.CreditLogPO;
import cn.edu.stu.seatkeeper.model.po.CreditPO;
import cn.edu.stu.seatkeeper.model.po.NotificationPO;
import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.model.po.SlotPO;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.exception.DataOperationException;
import cn.edu.stu.seatkeeper.service.CatalogDataService;
import cn.edu.stu.seatkeeper.service.CreditDataService;
import cn.edu.stu.seatkeeper.service.NotificationDataService;
import cn.edu.stu.seatkeeper.service.ReservationDataService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 在独立 MySQL 测试库验证约束、事务和竞争，不用内存数据库替代。 */
@SpringBootTest
class DataIntegrityIT {
    private static final String TEST_URL = System.getenv().getOrDefault("SEATKEEPER_TEST_DB_URL",
            "jdbc:mysql://127.0.0.1:33416/seatkeeper_test?serverTimezone=Asia/Shanghai");
    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 2, 8, 0);

    /**
     * 注入专用测试库连接和连接池配置，并在启动前拒绝不安全的测试目标。
     * @param registry 测试属性注册器
     */
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        TestDatabaseGuard.databaseName(TEST_URL);
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SEATKEEPER_TEST_DB_USER", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SEATKEEPER_TEST_DB_PASSWORD", ""));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "24");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET time_zone = '+08:00'");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired ReservationDataService reservations;
    @Autowired CreditDataService credits;
    @Autowired NotificationDataService notifications;
    @Autowired CatalogDataService catalog;
    @Autowired CreditMapper creditMapper;
    @Autowired PlatformTransactionManager transactionManager;

    /**
     * 仅清理专用测试库的业务表，再建立并发测试需要的用户、信用、座位与时段。
     */
    @BeforeEach
    void initializeOnlyTestBusinessData() {
        TestDatabaseGuard.clearBusinessData(jdbc, TEST_URL);
        for (int id = 1; id <= 200; id++) {
            jdbc.update("INSERT INTO app_user(id,student_no,name,password_hash,role,status) VALUES(?,?,?,?,?,?)",
                    id, "TEST" + id, "测试用户" + id, "LOGIN_DISABLED_TEST_FIXTURE", "STUDENT", "ACTIVE");
            jdbc.update("INSERT INTO credit(user_id,score) VALUES(?,100)", id);
        }
        jdbc.update("INSERT INTO seat(id,room,floor,seat_no,status) VALUES(1,'测试阅览室',1,'A01','AVAILABLE'),(2,'测试阅览室',1,'A02','AVAILABLE')");
        for (int i = 0; i < 7; i++) {
            jdbc.update("INSERT INTO slot(id,start_time,end_time) VALUES(?,?,?)", i + 1, START.plusHours(i * 2L), START.plusHours(i * 2L + 2));
        }
    }

    /**
     * 验证七张业务表、连接池、时区和隔离级别，并确认迁移重入不改变历史。
     */
    @Test
    void migrationHasSevenBusinessTablesAndRestartDoesNotRepeat() {
        assertThat(jdbc.getDataSource()).isInstanceOf(HikariDataSource.class);
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+08:00");
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("READ-COMMITTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(7);
        var migrationHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(migrationHistory).isNotEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(migrationHistory);
        assertThat(catalog.listSlots(START.minusDays(1), START.plusDays(1))).hasSize(7);
        assertThat(catalog.listSeats("测试阅览室", 1)).hasSize(2);
        assertThat(catalog.findUser(1L).studentNo()).isEqualTo("TEST1");
    }

    /**
     * 验证演示种子包含七个两小时时段，重复执行不重复插入记录。
     */
    @Test
    void demoScriptDefinesExactlySevenTwoHourSlotsAndCanBeReplayed() throws Exception {
        TestDatabaseGuard.clearBusinessData(jdbc, TEST_URL);
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/demo/V2__demo_data.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/demo/V2__demo_data.sql"));
        }
        List<SlotPO> slots = catalog.listSlots(LocalDateTime.now().toLocalDate().atStartOfDay(), LocalDateTime.now().plusDays(3));
        assertThat(slots).hasSize(7);
        assertThat(slots).allSatisfy(slot -> assertThat(slot.endTime()).isEqualTo(slot.startTime().plusHours(2)));
        assertThat(slots.stream().map(slot -> slot.startTime().getHour())).containsExactly(8, 10, 12, 14, 16, 18, 20);
        assertThat(credits.find(10002L).score()).isEqualTo(90);
        assertThat(jdbc.queryForObject("SELECT delta FROM credit_log WHERE id=10001", Integer.class)).isEqualTo(-10);
    }

    /**
     * 验证两百名用户并发争抢同座位同一时段时恰好一人成功。
     */
    @Test
    void twoHundredUsersCompeteForOneSeatWithExactlyOneWinner() throws Exception {
        List<Integer> results = concurrently(200, number -> {
            try {
                reservations.create(number + 1L, 1L, START, START.plusMinutes(15));
                return 1;
            } catch (DataOperationException exception) {
                assertThat(exception.code()).isEqualTo(DataOperationException.Code.RESERVATION_CONFLICT);
                return 0;
            }
        });
        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result == 0)).hasSize(199);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class)).isEqualTo(1);
    }

    /**
     * 验证同用户同一时段不能重复占座，取消提交后释放活动唯一键。
     */
    @Test
    void userCannotHoldTwoSeatsInSameSlotButCancellationReleasesBothKeys() {
        ReservationPO first = reservations.create(1L, 1L, START, START.plusMinutes(15));
        assertCode(() -> reservations.create(1L, 2L, START, START.plusMinutes(15)), DataOperationException.Code.RESERVATION_CONFLICT);
        assertThat(reservations.changeStatus(first.id(), 1L, 0, ReservationStatus.CANCELLED)).isEqualTo(1);
        assertThat(reservations.changeStatus(first.id(), 2L, 1, ReservationStatus.RESERVED)).isZero();
        ReservationPO replacement = reservations.create(1L, 1L, START, START.plusMinutes(15));
        assertThat(replacement.slotEnd()).isEqualTo(START.plusHours(2));
        assertThat(replacement.id()).isNotEqualTo(first.id());
    }

    /**
     * 验证过期状态释放占座约束，旧版本不能覆盖新的预约状态。
     */
    @Test
    void expiryReleasesKeysAndOldVersionCannotOverwriteNewState() {
        ReservationPO first = reservations.create(1L, 1L, START, START.plusMinutes(15));
        assertThat(reservations.changeStatus(first.id(), 1L, 0, ReservationStatus.EXPIRED)).isEqualTo(1);
        assertThat(reservations.changeStatus(first.id(), 1L, 0, ReservationStatus.CHECKED_IN)).isZero();
        assertThat(reservations.find(first.id()).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservations.create(1L, 1L, START, START.plusMinutes(15))).isNotNull();
    }

    /**
     * 验证未登记时段和伪造结束时间分别被插入逻辑与外键约束拒绝。
     */
    @Test
    void arbitrarySlotIsRejectedByInsertSelectAndForeignKey() {
        assertCode(() -> reservations.create(1L, 1L, START.plusMinutes(1), START.plusMinutes(16)), DataOperationException.Code.SLOT_NOT_FOUND);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reservation(user_id,seat_id,slot_start,slot_end,status,check_in_deadline) VALUES(1,1,?,?,'RESERVED',?)", START.plusMinutes(1), START.plusHours(2), START.plusMinutes(15))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reservation(user_id,seat_id,slot_start,slot_end,status,check_in_deadline) VALUES(1,1,?,?,'RESERVED',?)", START, START.plusHours(3), START.plusMinutes(15))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /**
     * 验证同一信用请求并发重放一百次只产生一条流水和一次扣分。
     */
    @Test
    void concurrentCreditReplayCreatesOneLogAndDeductsOnlyOnce() throws Exception {
        ReservationPO reservation = reservations.create(1L, 1L, START, START.plusMinutes(15));
        List<CreditLogPO> logs = concurrently(100, number -> credits.applyDelta(1L, reservation.id(), -5, "NO_SHOW"));
        assertThat(logs.stream().map(CreditLogPO::id).distinct()).hasSize(1);
        assertThat(credits.find(1L).score()).isEqualTo(95);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_log", Integer.class)).isEqualTo(1);
        assertThat(credits.find(1L).version()).isEqualTo(1);
    }

    /**
     * 验证余额越界完整回滚，并拒绝预约归属不符或幂等重放变参。
     */
    @Test
    void creditRangeFailureRollsBackAndOwnershipAndChangedReplayAreRejected() {
        ReservationPO reservation = reservations.create(1L, 1L, START, START.plusMinutes(15));
        assertCode(() -> credits.applyDelta(1L, reservation.id(), -101, "BAD"), DataOperationException.Code.CREDIT_OUT_OF_RANGE);
        assertCode(() -> credits.applyDelta(1L, reservation.id(), 1, "BAD"), DataOperationException.Code.CREDIT_OUT_OF_RANGE);
        assertThat(credits.find(1L).score()).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_log", Integer.class)).isZero();
        assertCode(() -> credits.applyDelta(2L, reservation.id(), -5, "NO_SHOW"), DataOperationException.Code.OWNERSHIP_MISMATCH);
        CreditLogPO first = credits.applyDelta(1L, reservation.id(), -5, "NO_SHOW");
        assertThat(credits.applyDelta(1L, reservation.id(), -5, "NO_SHOW").id()).isEqualTo(first.id());
        assertCode(() -> credits.applyDelta(1L, reservation.id(), -6, "NO_SHOW"), DataOperationException.Code.IDEMPOTENCY_MISMATCH);
        assertCode(() -> credits.applyDelta(2L, reservation.id(), -5, "NO_SHOW"), DataOperationException.Code.OWNERSHIP_MISMATCH);
        assertThat(credits.find(1L).score()).isEqualTo(95);
    }

    /**
     * 验证数据库信用范围约束生效，旧版本更新不能成功。
     */
    @Test
    void creditCheckConstraintAndVersionAreEnforced() {
        assertThatThrownBy(() -> jdbc.update("UPDATE credit SET score=101 WHERE user_id=1"))
                .isInstanceOfSatisfying(org.springframework.dao.DataAccessException.class,
                        exception -> assertThat(((SQLException) exception.getMostSpecificCause()).getErrorCode()).isEqualTo(3819));
        assertThat(credits.updateRestriction(1L, 0, START)).isEqualTo(1);
        assertThat(credits.updateRestriction(1L, 0, START.plusDays(1))).isZero();
        assertThat(credits.find(1L).restrictedUntil()).isEqualTo(START);
    }

    /**
     * 验证调用方外层事务失败时，余额和流水一起回滚。
     */
    @Test
    void callerTransactionRollbackUndoesBalanceAndLogTogether() {
        ReservationPO reservation = reservations.create(1L, 1L, START, START.plusMinutes(15));
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            credits.applyDelta(1L, reservation.id(), -5, "NO_SHOW");
            throw new IllegalStateException("模拟同一业务事务后续步骤失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(credits.find(1L).score()).isEqualTo(100);
        assertThat(credits.find(1L).version()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_log", Integer.class)).isZero();
    }

    /**
     * 验证通知读取与已读标记按用户隔离，游标顺序正确且清理有界。
     */
    @Test
    void notificationsAreUserScopedOrderedAndCleanupIsBounded() {
        NotificationPO first = notifications.send(1L, "RESERVATION", "第一条");
        NotificationPO foreign = notifications.send(2L, "RESERVATION", "其他用户");
        NotificationPO second = notifications.send(1L, "CREDIT", "第二条");
        NotificationPO third = notifications.send(1L, "CREDIT", "第三条");
        assertThat(notifications.page(1L, 0, 2).stream().map(NotificationPO::id)).containsExactly(third.id(), second.id());
        assertThat(notifications.page(1L, 2, 2).stream().map(NotificationPO::id)).containsExactly(first.id());
        assertThat(notifications.after(1L, first.id(), 1).stream().map(NotificationPO::id)).containsExactly(second.id());
        assertThat(notifications.markRead(1L, List.of(foreign.id()))).isZero();
        assertThat(notifications.markRead(1L, List.of(first.id(), second.id()))).isEqualTo(2);
        NotificationPO fourth = notifications.send(1L, "RESERVATION", "另一条可清理消息");
        assertThat(notifications.markRead(1L, List.of(fourth.id()))).isEqualTo(1);
        LocalDateTime cutoff = START;
        jdbc.update("UPDATE notification SET created_at=?", cutoff.minusDays(1));
        jdbc.update("UPDATE notification SET created_at=? WHERE id=?", cutoff, second.id());
        assertThat(notifications.cleanupReadBefore(cutoff, 1)).isEqualTo(1);
        assertThat(notifications.after(1L, 0, 10).stream().map(NotificationPO::id)).containsExactly(second.id(), third.id(), fourth.id());
        assertThat(notifications.after(2L, 0, 10).stream().map(NotificationPO::id)).containsExactly(foreign.id());
        assertThat(notifications.cleanupReadBefore(cutoff, 1)).isEqualTo(1);
        assertThat(notifications.cleanupReadBefore(cutoff, 1)).isZero();
        assertThatThrownBy(() -> notifications.after(1L, 0, 1001)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证后一通知等待同用户前一事务提交后才分配标识，避免游标越过未提交消息。
     */
    @Test
    void notificationSendWaitsForEarlierUserTransactionBeforeAllocatingNextCursor() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch reservationsReady = new CountDownLatch(2);
        CountDownLatch firstSent = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch allowSecondSend = new CountDownLatch(1);
        CountDownLatch secondEnteringSend = new CountDownLatch(1);
        CountDownLatch secondReturned = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                // 两个外层事务先取得预约外键的用户共享锁，禁止用用户排他锁实现发送串行化。
                reservations.create(1L, 1L, START, START.plusMinutes(15));
                reservationsReady.countDown();
                await(reservationsReady);
                NotificationPO notification = notifications.send(1L, "RESERVATION", "先插入后提交");
                firstSent.countDown();
                await(allowFirstCommit);
                return notification;
            }));
            var second = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                reservations.create(1L, 2L, START.plusHours(2), START.plusHours(2).plusMinutes(15));
                reservationsReady.countDown();
                await(reservationsReady);
                await(allowSecondSend);
                secondEnteringSend.countDown();
                NotificationPO notification = notifications.send(1L, "RESERVATION", "同用户后发送");
                secondReturned.countDown();
                return notification;
            }));
            assertThat(firstSent.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(notifications.after(1L, 0, 10)).isEmpty();
            allowSecondSend.countDown();
            assertThat(secondEnteringSend.await(15, TimeUnit.SECONDS)).isTrue();
            boolean returnedBeforeFirstCommit = secondReturned.await(1, TimeUnit.SECONDS);
            if (returnedBeforeFirstCommit) second.get(15, TimeUnit.SECONDS);
            List<NotificationPO> visibleBeforeFirstCommit = notifications.after(1L, 0, 10);
            allowFirstCommit.countDown();
            NotificationPO firstNotification = first.get(15, TimeUnit.SECONDS);
            List<NotificationPO> initialPage = notifications.after(1L, 0, 1);
            NotificationPO secondNotification = second.get(15, TimeUnit.SECONDS);
            List<NotificationPO> followingPage = notifications.after(1L, initialPage.get(0).id(), 10);
            System.out.println("notification_commit_probe premature=" + returnedBeforeFirstCommit
                    + ", visible_before_first_commit=" + visibleBeforeFirstCommit.size()
                    + ", first_id=" + firstNotification.id() + ", second_id=" + secondNotification.id());
            assertThat(returnedBeforeFirstCommit).as("同用户后发送必须等待先发送事务提交").isFalse();
            assertThat(visibleBeforeFirstCommit).isEmpty();
            assertThat(initialPage.stream().map(NotificationPO::id)).containsExactly(firstNotification.id());
            assertThat(followingPage.stream().map(NotificationPO::id)).containsExactly(secondNotification.id());
        } finally {
            allowFirstCommit.countDown();
            allowSecondSend.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * 验证缺少信用锁行的用户不能发送通知，也不会插入不具备顺序保证的记录。
     */
    @Test
    void notificationRefusesUserWithoutCreditLockRow() {
        jdbc.update("INSERT INTO app_user(id,student_no,name,password_hash,role,status) VALUES(201,'NO_CREDIT','未开户测试','LOGIN_DISABLED_TEST_FIXTURE','STUDENT','ACTIVE')");
        assertCode(() -> notifications.send(201L, "RESERVATION", "不能缺少用户串行化锁"), DataOperationException.Code.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=201", Integer.class)).isZero();
    }

    /**
     * 验证不同用户首次并发写入信用流水时不会发生缺失键间隙锁死锁。
     */
    @Test
    void differentUsersCanInsertFirstCreditLogsWithoutGapLockDeadlock() throws Exception {
        ReservationPO first = reservations.create(1L, 1L, START, START.plusMinutes(15));
        ReservationPO second = reservations.create(2L, 2L, START, START.plusMinutes(15));
        CountDownLatch bothMissingKeysRead = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(first, second).stream().map(reservation -> pool.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        jdbc.execute("SET SESSION innodb_lock_wait_timeout = 5");
                        System.out.println("credit_gap_lock_probe isolation="
                                + jdbc.queryForObject("SELECT @@transaction_isolation", String.class));
                        CreditPO credit = creditMapper.lockByUser(reservation.userId());
                        assertThat(creditMapper.findLog(reservation.id(), "FIRST_EVENT")).isNull();
                        bothMissingKeysRead.countDown();
                        await(bothMissingKeysRead);
                        assertThat(creditMapper.updateScore(credit.userId(), credit.version(), 95)).isEqualTo(1);
                        creditMapper.insertLog(credit.userId(), reservation.id(), -5, "FIRST_EVENT", 95);
                        return creditMapper.findLog(reservation.id(), "FIRST_EVENT");
                    }))).toList();
            for (var future : futures) assertThat(future.get(20, TimeUnit.SECONDS)).isNotNull();
            assertThat(credits.find(1L).score()).isEqualTo(95);
            assertThat(credits.find(2L).score()).isEqualTo(95);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_log", Integer.class)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * 等待并发测试闸门，超时或中断时明确终止测试并保留线程中断标记。
     * @param latch 并发同步闸门
     */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试闸门超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", exception);
        }
    }

    /**
     * 执行预期失败的操作，并断言抛出的领域异常包含指定错误码。
     * @param operation 待执行的测试操作
     * @param expected 已验证的目标库名或预期领域错误码
     */
    private static void assertCode(Runnable operation, DataOperationException.Code expected) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(DataOperationException.class,
                exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    /**
     * 在所有工作线程就绪后同时放行，收集真实并发结果并始终回收线程池。
     * @param count 并发工作线程数
     * @param operation 待执行的测试操作
     */
    private static <T> List<T> concurrently(int count, IndexedOperation<T> operation) throws Exception {
        // 所有调用在同一闸门释放；连接池排队仍由真实数据库裁决竞争。
        var pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<T>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int index = i;
            tasks.add(() -> { ready.countDown(); start.await(); return operation.run(index); });
        }
        try {
            var futures = tasks.stream().map(pool::submit).toList();
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(90, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @FunctionalInterface
    interface IndexedOperation<T> {
        /**
         * 执行一个带线程序号的并发测试操作，将操作异常交给测试调用方。
         * @param index 工作线程从零开始的序号
         */
        T run(int index) throws Exception;
    }
}

package com.bugaoshan.security;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.mapper.RecoveryMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.AccountSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only: the runner creates and migrates a disposable MySQL instance, never an application DB. */
class AccountRecoveryMySqlIT extends AccountRecoveryTest {
    @Override
    DataSource createDataSource() {
        String url = System.getenv("COURSEHUB_RECOVERY_MYSQL_URL");
        assertNotNull(url, "Run scripts/verify-account-recovery-mysql.ps1; this suite never silently skips");
        URI uri = URI.create(url.substring("jdbc:".length()));
        assertEquals("mysql", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertTrue(uri.getPort() > 0);
        assertTrue(uri.getPath().matches("/coursehub_recovery_check_[0-9a-f]{32}"), "Refusing a non-disposable DB");
        assertNull(uri.getUserInfo());
        return new DriverManagerDataSource(url, System.getenv("COURSEHUB_RECOVERY_MYSQL_USER"),
                System.getenv("COURSEHUB_RECOVERY_MYSQL_PASSWORD"));
    }

    @Override
    void initializeDatabase() {
        String nonce = System.getenv("COURSEHUB_RECOVERY_MYSQL_NONCE");
        assertNotNull(nonce);
        assertTrue(nonce.matches("[0-9a-f]{32}"));
        assertEquals(nonce, db.queryForObject("SELECT nonce FROM recovery_test_guard", String.class),
                "Refusing fixture cleanup without this run's guard");
        assertTrue(db.queryForObject("SELECT VERSION()", String.class).startsWith("8."));
        assertEquals("REPEATABLE-READ", db.queryForObject("SELECT @@transaction_isolation", String.class));
        assertEquals("utf8mb4_unicode_ci", db.queryForObject("SELECT TABLE_COLLATION FROM information_schema.tables"
                + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user'", String.class));
        for (String table : List.of("user", "recovery_code", "recovery_rate_lock", "recovery_rate_event")) {
            assertEquals("InnoDB", db.queryForObject("SELECT ENGINE FROM information_schema.tables"
                    + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", String.class, table));
        }
        // The complete init.sql + migration already ran once. Reset only fixtures in the guarded test DB.
        db.update("DELETE FROM recovery_code");
        db.update("DELETE FROM recovery_rate_event");
        db.update("DELETE FROM recovery_rate_lock");
        db.update("DELETE FROM user");
    }

    @Override void ageRateEvents(int seconds) {
        db.update("UPDATE recovery_rate_event SET created_at=TIMESTAMPADD(SECOND,?,created_at)", -seconds);
    }
    @Override void expireCode(String id) {
        db.update("UPDATE recovery_code SET expires_at=TIMESTAMPADD(MINUTE,-1,CURRENT_TIMESTAMP(6)) WHERE request_id=?", id);
    }

    /** Separate datasource, MyBatis session factory and service: only the database is shared. */
    AccountSecurityService instance(BiConsumer<String, Object[]> afterStatement) throws Exception {
        DataSource source = createDataSource();
        var session = mapperSession(source);
        RecoveryMapper real = session.getMapper(RecoveryMapper.class);
        RecoveryMapper observed = (RecoveryMapper) Proxy.newProxyInstance(RecoveryMapper.class.getClassLoader(),
                new Class<?>[]{RecoveryMapper.class}, (proxy, method, args) -> {
                    Object result;
                    try { result = method.invoke(real, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                    afterStatement.accept(method.getName(), args);
                    return result;
                });
        return newService(session.getMapper(UserMapper.class), observed, source);
    }

    static void await(CountDownLatch latch, int milliseconds) {
        try { latch.await(milliseconds, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @Test void existingIpScopeSerializesAcrossInstancesWithoutLockUpgradeDeadlock() throws Exception {
        String key = RecoveryCrypto.scopeKey("ip:shared-ip");
        db.update("INSERT INTO recovery_rate_lock(scope_key) VALUES(?)", key);
        var ensured = new CountDownLatch(2);
        BiConsumer<String, Object[]> pause = (method, args) -> {
            if (method.equals("ensureRateLock") && key.equals(args[0])) {
                ensured.countDown();
                // INSERT IGNORE lets both hold S locks here; an exclusive upsert makes the second wait.
                await(ensured, 400);
            }
        };
        var one = instance(pause); var two = instance(pause);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return one.requestResetCode("unknown01", "safe@example.com", "shared-ip"); });
            var b = pool.submit(() -> { start.await(); return two.requestResetCode("unknown02", "safe@example.com", "shared-ip"); });
            start.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS).message(), b.get(10, TimeUnit.SECONDS).message());
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM recovery_rate_event WHERE scope_key=?", Integer.class, key));
            assertEquals(2, queue.size());
        } finally { pool.shutdownNow(); }
    }

    @Test void coldStartOfDisjointAccountsDoesNotDeadlockOnEmptyEventRanges() throws Exception {
        var read = new CountDownLatch(2);
        BiConsumer<String, Object[]> pause = (method, args) -> {
            if (method.equals("recentEvents")) { read.countDown(); await(read, 1000); }
        };
        var one = instance(pause); var two = instance(pause);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return one.requestResetCode("unknown01", "safe@example.com", "first-ip"); });
            var b = pool.submit(() -> { start.await(); return two.requestResetCode("unknown02", "safe@example.com", "second-ip"); });
            start.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS).message(), b.get(10, TimeUnit.SECONDS).message());
            assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM recovery_rate_event", Integer.class));
            assertEquals(2, queue.size());
        } finally { pool.shutdownNow(); }
    }

    @Test void firstBindingForDifferentUsersProducesBothCodesWithoutGapDeadlock() throws Exception {
        var invalidated = new CountDownLatch(2);
        BiConsumer<String, Object[]> pause = (method, args) -> {
            if (method.equals("invalidatePurpose")) { invalidated.countDown(); await(invalidated, 1000); }
        };
        var one = instance(pause); var two = instance(pause);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return one.requestBindingCode(1L, "old-pass", "first@example.com", "first-ip"); });
            var b = pool.submit(() -> { start.await(); return two.requestBindingCode(2L, "other-pass", "second@example.com", "second-ip"); });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE send_status='ACTIVE'", Integer.class));
            assertEquals(2, delivered.size());
        } finally { pool.shutdownNow(); }
    }

    @Test void collationEquivalentAccountsShareCanonicalSendIntervalAndHourlyLimit() {
        bind("safe@example.com"); delivered.clear();
        var aliases = List.of("STUDENT01", "stüdent01", "studént01", "student01", "StUdEnT01");
        String first = reset(aliases.getFirst(), "safe@example.com", "alias-first-ip");
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE purpose='RESET'", Integer.class));
        reset(aliases.get(1), "safe@example.com", "alias-minute-ip");
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE purpose='RESET'", Integer.class));
        assertEquals("ACTIVE", db.queryForObject("SELECT send_status FROM recovery_code WHERE request_id=?", String.class, first));
        for (int i = 1; i < 5; i++) {
            ageRateEvents(61); reset(aliases.get(i), "safe@example.com", "alias-ip-" + i);
        }
        ageRateEvents(61); reset("stùdent01", "safe@example.com", "alias-sixth-ip");
        assertEquals(5, db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE purpose='RESET'", Integer.class));
    }

    @Test void databaseEnforcesRecoveryEmailUniquenessAndForeignKey() {
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM user WHERE recovery_email IS NULL", Integer.class));
        db.update("UPDATE user SET recovery_email='safe@example.com' WHERE user_id=1");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> db.update("UPDATE user SET recovery_email='SAFE@EXAMPLE.COM' WHERE user_id=2"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO recovery_code(request_id,user_id,purpose,email,credential_version,code_hash,expires_at,send_status,created_at)"
                        + " VALUES(UUID(),999,'BIND','safe@example.com',0,REPEAT('a',64),NOW(),'PENDING',NOW())"));
    }

    @Test void concurrentIpBudgetAcrossInstancesAllowsExactlyTwentyReservations() throws Exception {
        var one = instance((method, args) -> {}); var two = instance((method, args) -> {});
        var pool = Executors.newFixedThreadPool(8); var start = new CountDownLatch(1);
        try {
            var jobs = new ArrayList<Future<AccountSecurityService.CodeResponse>>();
            for (int i = 0; i < 25; i++) {
                String account = "unknown" + i;
                var app = i % 2 == 0 ? one : two;
                jobs.add(pool.submit(() -> { start.await(); return app.requestResetCode(account, "safe@example.com", "budget-ip"); }));
            }
            start.countDown();
            for (var job : jobs) assertNotNull(job.get(10, TimeUnit.SECONDS).requestId());
            assertEquals(20, queue.size());
            assertEquals(20, db.queryForObject("SELECT COUNT(*) FROM recovery_rate_event WHERE scope_key=?",
                    Integer.class, RecoveryCrypto.scopeKey("ip:budget-ip")));
            assertEquals(40, db.queryForObject("SELECT COUNT(*) FROM recovery_rate_event", Integer.class),
                    "Rejected requests must not leave a partial account reservation");
        } finally { pool.shutdownNow(); }
    }

    @Test void competingEmailBindingsCommitOneAndRollBackTheOtherConsumption() throws Exception {
        String first = service.requestBindingCode(1L, "old-pass", "safe@example.com", "first-ip").requestId();
        String firstCode = delivered.get("safe@example.com");
        ageRateEvents(61);
        String second = service.requestBindingCode(2L, "other-pass", "safe@example.com", "second-ip").requestId();
        String secondCode = delivered.get("safe@example.com");
        var one = instance((method, args) -> {}); var two = instance((method, args) -> {});
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); try { one.bindEmail(1L, "old-pass", "safe@example.com", first, firstCode); return true; }
                catch (BusinessException e) { assertEquals(409, e.getCode()); return false; } });
            var b = pool.submit(() -> { start.await(); try { two.bindEmail(2L, "other-pass", "safe@example.com", second, secondCode); return true; }
                catch (BusinessException e) { assertEquals(409, e.getCode()); return false; } });
            start.countDown(); assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM user WHERE recovery_email='safe@example.com'", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE consumed_at IS NOT NULL", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE consumed_at IS NULL AND send_status='ACTIVE'", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void lateActivationAndPasswordChangeUseTheSameLockOrderWithoutDeadlock() throws Exception {
        long before = deadlocks();
        var mailEntered = new CountDownLatch(1); var mailComplete = new CountDownLatch(1);
        duringSend = () -> { mailEntered.countDown(); await(mailComplete, 5000); };
        var pool = Executors.newSingleThreadExecutor();
        try {
            var sending = pool.submit(() -> {
                try { service.requestBindingCode(1L, "old-pass", "safe@example.com", "late-ip"); return true; }
                catch (BusinessException e) { assertEquals(503, e.getCode()); return false; }
            });
            assertTrue(mailEntered.await(5, TimeUnit.SECONDS));
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            transaction.executeWithoutResult(status -> {
                users.selectForUpdate(1L);
                mailComplete.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (db.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class) == 0
                        && System.nanoTime() < deadline) {
                    try { Thread.sleep(10); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                }
                assertTrue(db.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class) > 0,
                        "Activation must actually be contending with the held user row");
                service.changePassword(1L, "old-pass", "new-pass");
            });
            assertFalse(sending.get(10, TimeUnit.SECONDS));
            assertEquals(before, deadlocks(), "A swallowed delivery exception must not hide a DB deadlock");
            assertEquals(1, users.selectById(1L).getCredentialVersion());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE send_status='ACTIVE'", Integer.class));
            assertTrue(encoder.matches("new-pass", users.selectById(1L).getPasswordHash()));
        } finally { mailComplete.countDown(); pool.shutdownNow(); }
    }

    long deadlocks() {
        return db.queryForObject("SELECT COUNT FROM information_schema.innodb_metrics WHERE NAME='lock_deadlocks'", Long.class);
    }
}

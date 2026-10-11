package com.bugaoshan.security;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.mapper.RecoveryMapper;
import com.bugaoshan.service.AccountSecurityService;
import com.bugaoshan.service.RecoveryCodeService;
import com.bugaoshan.service.RecoveryRateLimiter;
import com.bugaoshan.mail.RecoveryMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the real service, MyBatis statements and Spring transactions; no SMTP or existing DB. */
class AccountRecoveryTest {
    JdbcTemplate db;
    DataSource dataSource;
    RecoveryMapper recoveryMapper;
    UserMapper users;
    AccountSecurityService service;
    RecoveryCodeService codes;
    List<Runnable> queue;
    final Map<String, String> delivered = new ConcurrentHashMap<>();
    boolean configured;
    boolean failMail;
    boolean rejectQueue;
    Runnable duringSend;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setup() throws Exception {
        configured = true; failMail = false; rejectQueue = false; duringSend = null; delivered.clear(); queue = new CopyOnWriteArrayList<>();
        dataSource = createDataSource();
        db = new JdbcTemplate(dataSource);
        initializeDatabase();
        db.update("INSERT INTO user(user_id,username,password_hash,email,nickname,status) VALUES(1,'student01',?,'contact@example.com','昵称甲',1)", encoder.encode("old-pass"));
        db.update("INSERT INTO user(user_id,username,password_hash,email,nickname,status) VALUES(2,'student02',?,'second@example.com','昵称乙',1)", encoder.encode("other-pass"));
        var session = mapperSession(dataSource); users = session.getMapper(UserMapper.class);
        recoveryMapper = session.getMapper(RecoveryMapper.class);
        codes = new RecoveryCodeService(recoveryMapper, new RecoveryCrypto("separate-test-recovery-key-at-least-32-bytes"));
        service = newService(users, recoveryMapper, dataSource);
    }

    DataSource createDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
    }

    void initializeDatabase() throws Exception {
        db.execute("CREATE TABLE user(user_id BIGINT PRIMARY KEY, username VARCHAR(20), password_hash VARCHAR(100),"
                + " email VARCHAR(254), nickname VARCHAR(20), status INT, is_deleted INT DEFAULT 0,"
                + " user_type INT DEFAULT 1, update_time DATETIME, last_login_time DATETIME)");
        // Execute the checked-in incremental migration against an empty disposable database.
        var sql = new ClassPathResource("db/migrations/2026-10-10-account-recovery.sql");
        try (var connection = dataSource.getConnection(); var reader = new java.io.InputStreamReader(sql.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            org.h2.tools.RunScript.execute(connection, reader);
        }
    }

    SqlSessionTemplate mapperSession(DataSource source) throws Exception {
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        var config = new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/UserMapper.xml"), new ClassPathResource("mapper/RecoveryMapper.xml"));
        return new SqlSessionTemplate(factory.getObject());
    }

    AccountSecurityService newService(UserMapper userMapper, RecoveryMapper mapper, DataSource source) {
        RecoveryMailSender mail = new RecoveryMailSender() {
            public void requireConfigured() { if (!configured) throw new BusinessException(503, "邮件服务尚未配置"); }
            public void send(String email, String code, String purpose) {
                if (duringSend != null) duringSend.run();
                if (failMail) throw new IllegalStateException("simulated SMTP failure");
                delivered.put(email, code);
            }
        };
        return new AccountSecurityService(userMapper,
                new RecoveryCodeService(mapper, new RecoveryCrypto("separate-test-recovery-key-at-least-32-bytes")),
                new RecoveryRateLimiter(mapper), mail,
                task -> { if(rejectQueue) throw new RejectedExecutionException(); queue.add(task); },
                encoder, new DataSourceTransactionManager(source));
    }

    String bind(String email) {
        var result = service.requestBindingCode(1L, "old-pass", email, "127.0.0.1");
        String id = result.requestId();
        service.bindEmail(1L, "old-pass", email, id, delivered.get(email.trim().toLowerCase()));
        return id;
    }
    String reset(String account, String email, String ip) {
        String id = service.requestResetCode(account, email, ip).requestId();
        if (!queue.isEmpty()) queue.removeFirst().run();
        return id;
    }

    @Test void contactEmailCannotRecoverAndAllMismatchRequestsHaveTheSameShape() {
        var known = service.requestResetCode("student01", "contact@example.com", "ip1");
        var absent = service.requestResetCode("unknown01", "contact@example.com", "ip2");
        assertEquals(known.message(), absent.message());
        assertDoesNotThrow(() -> UUID.fromString(absent.requestId()));
        queue.forEach(Runnable::run);
        assertTrue(delivered.isEmpty());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM recovery_code", Integer.class));
    }
    @Test void originalPasswordIsRequiredAndBindingDoesNotOverwriteContactEmail() {
        assertThrows(BusinessException.class, () -> service.requestBindingCode(1L,"incorrect","safe@example.com","ip"));
        assertThrows(BusinessException.class, () -> service.changePassword(1L,"incorrect","new-pass"));
        bind("  SAFE@EXAMPLE.COM  ");
        assertEquals("contact@example.com", users.selectById(1L).getEmail());
        assertEquals("safe@example.com", users.selectById(1L).getRecoveryEmail());
        assertTrue(service.security(1L).recoveryEmailVerified());
        assertFalse(service.security(1L).recoveryEmailMasked().contains("safe@"));
    }
    @Test void wrongAttemptsCommitAndTheSixthSubmissionIsLockedOut() {
        String id = service.requestBindingCode(1L,"old-pass","safe@example.com","ip").requestId();
        String wrong = delivered.get("safe@example.com").equals("000000") ? "111111" : "000000";
        for (int i=0; i<5; i++) assertThrows(BusinessException.class, () -> service.bindEmail(1L,"old-pass","safe@example.com",id,wrong));
        assertEquals(5, db.queryForObject("SELECT attempts FROM recovery_code WHERE request_id=?", Integer.class,id));
        assertThrows(BusinessException.class, () -> service.bindEmail(1L,"old-pass","safe@example.com",id,delivered.get("safe@example.com")));
        assertNull(users.selectById(1L).getRecoveryEmail());
    }
    @Test void expiryPurposeAndOwnerCannotBeBypassed() {
        String id = service.requestBindingCode(1L,"old-pass","safe@example.com","ip").requestId();
        String code = delivered.get("safe@example.com");
        assertThrows(BusinessException.class, () -> service.resetPassword("student01","safe@example.com",id,code,"new-pass"));
        assertThrows(BusinessException.class, () -> service.bindEmail(2L,"other-pass","safe@example.com",id,code));
        assertThrows(BusinessException.class, () -> service.bindEmail(1L,"old-pass","wrong@example.com",id,code));
        expireCode(id);
        assertThrows(BusinessException.class, () -> service.bindEmail(1L,"old-pass","safe@example.com",id,code));
    }
    @Test void verifiedRecoveryChangesOnlyPasswordAndInvalidatesBothKindsOfTokens() {
        bind("safe@example.com");
        var jwt = new JwtUtil("test-secret-key-that-is-long-enough-for-hs256",60000,120000);
        String access=jwt.generateAccessToken(1L,1,0), refresh=jwt.generateRefreshToken(1L,0);
        String id=reset("student01","safe@example.com","reset-ip");
        service.resetPassword("student01","safe@example.com",id,delivered.get("safe@example.com"),"new-pass");
        User user=users.selectById(1L);
        assertTrue(encoder.matches("new-pass",user.getPasswordHash()));
        assertFalse(encoder.matches("old-pass",user.getPasswordHash()));
        assertEquals("昵称甲",user.getNickname()); assertEquals("student01",user.getUsername());
        assertEquals(1,user.getCredentialVersion());
        assertFalse(jwt.matchesCredentialVersion(access,user.getCredentialVersion()));
        assertFalse(jwt.matchesCredentialVersion(refresh,user.getCredentialVersion()));
        assertThrows(BusinessException.class, () -> service.resetPassword("student01","safe@example.com",id,delivered.get("safe@example.com"),"again-pass"));
    }
    @Test void passwordChangeInvalidatesPendingCodesAndOldPassword() {
        String id=service.requestBindingCode(1L,"old-pass","safe@example.com","ip").requestId();
        service.changePassword(1L,"old-pass","new-pass");
        assertThrows(BusinessException.class, () -> service.bindEmail(1L,"new-pass","safe@example.com",id,delivered.get("safe@example.com")));
        assertThrows(BusinessException.class, () -> service.changePassword(1L,"old-pass","again-pass"));
        assertEquals(1,users.selectById(1L).getCredentialVersion());
    }
    @Test void lateSmtpResultCannotEnableCodeAfterPasswordChange() {
        duringSend=() -> service.changePassword(1L,"old-pass","new-pass");
        assertThrows(BusinessException.class, () -> service.requestBindingCode(1L,"old-pass","safe@example.com","ip"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE send_status='ACTIVE'",Integer.class));
    }
    @Test void unconfiguredAndFailedMailNeverProduceConsumableCodes() {
        configured=false;
        assertThrows(BusinessException.class, () -> service.requestResetCode("student01","safe@example.com","ip"));
        assertThrows(BusinessException.class, () -> service.requestBindingCode(1L,"old-pass","safe@example.com","ip"));
        configured=true; failMail=true;
        assertThrows(BusinessException.class, () -> service.requestBindingCode(1L,"old-pass","safe@example.com","ip"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE send_status='ACTIVE'",Integer.class));
    }
    @Test void resendInvalidatesPreviousCodeAndMinuteLimitSurvivesAnotherIp() {
        String old=service.requestBindingCode(1L,"old-pass","safe@example.com","ip1").requestId();
        String oldCode=delivered.get("safe@example.com");
        assertThrows(BusinessException.class, () -> service.requestBindingCode(1L,"old-pass","safe@example.com","ip2"));
        ageRateEvents(61);
        String latest=service.requestBindingCode(1L,"old-pass","safe@example.com","ip2").requestId();
        assertFalse(codes.activate(old),"A late SMTP completion must not reactivate the superseded request");
        assertThrows(BusinessException.class, () -> service.bindEmail(1L,"old-pass","safe@example.com",old,oldCode));
        service.bindEmail(1L,"old-pass","safe@example.com",latest,delivered.get("safe@example.com"));
    }
    @Test void accountHourlyLimitAndIpHourlyLimitIncludeUnknownAccounts() {
        for(int i=0;i<5;i++) { service.requestResetCode("unknown01","safe@example.com","ip"+i); ageRateEvents(61); }
        assertEquals(5,queue.size());
        service.requestResetCode("UNKNOWN01","safe@example.com","another-ip");
        assertEquals(5,queue.size());
        queue.clear();
        for(int i=0;i<20;i++) service.requestResetCode("account"+i,"safe@example.com","shared-ip");
        assertEquals(20,queue.size());
        service.requestResetCode("anotheraccount","safe@example.com","shared-ip");
        assertEquals(20,queue.size());
    }
    @Test void bindingEmailRateLimitIsSharedAcrossAccounts() {
        service.requestBindingCode(1L,"old-pass","safe@example.com","ip1");
        assertThrows(BusinessException.class, () -> service.requestBindingCode(2L,"other-pass","SAFE@example.com","ip2"));
    }
    @Test void duplicateEmailRollsBackConsumptionAndChangingEmailRevokesOldResetCode() {
        bind("safe@example.com");
        String reset=reset("student01","safe@example.com","reset-ip");
        String resetCode=delivered.get("safe@example.com");
        ageRateEvents(61);
        String second=service.requestBindingCode(2L,"other-pass","safe@example.com","ip2").requestId();
        assertThrows(BusinessException.class, () -> service.bindEmail(2L,"other-pass","safe@example.com",second,delivered.get("safe@example.com")));
        assertNull(db.queryForObject("SELECT consumed_at FROM recovery_code WHERE request_id=?",java.sql.Timestamp.class,second));
        ageRateEvents(61); bind("new@example.com");
        assertThrows(BusinessException.class, () -> service.resetPassword("student01","safe@example.com",reset,resetCode,"new-pass"));
    }
    @Test void twoConcurrentConsumersOnlyChangePasswordOnce() throws Exception {
        bind("safe@example.com");
        String id=reset("student01","safe@example.com","ip-reset"), code=delivered.get("safe@example.com");
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Boolean> action=() -> { start.await(); try { service.resetPassword("student01","safe@example.com",id,code,"new-pass"); return true; } catch(BusinessException e) { return false; } };
        try {
            var one=pool.submit(action); var two=pool.submit(action); start.countDown();
            assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
            assertEquals(1,users.selectById(1L).getCredentialVersion());
        } finally { pool.shutdownNow(); }
    }
    void ageRateEvents(int seconds) { db.update("UPDATE recovery_rate_event SET created_at=DATEADD('SECOND',?,created_at)",-seconds); }
    void expireCode(String id) { db.update("UPDATE recovery_code SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE request_id=?",id); }

    @Test void concurrentRequestsShareTheAccountSendInterval() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Boolean> send=() -> { start.await(); try { service.requestBindingCode(1L,"old-pass","safe@example.com",Thread.currentThread().getName()); return true; } catch(BusinessException e) { return false; } };
        try {
            var one=pool.submit(send); var two=pool.submit(send); start.countDown();
            assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM recovery_code",Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentWrongSubmissionsCommitExactlyFiveAttempts() throws Exception {
        String id=service.requestBindingCode(1L,"old-pass","safe@example.com","ip").requestId();
        String wrong=delivered.get("safe@example.com").equals("000000")?"111111":"000000";
        var pool=Executors.newFixedThreadPool(4); var start=new CountDownLatch(1);
        try {
            var jobs=new ArrayList<Future<?>>();
            for(int i=0;i<10;i++) jobs.add(pool.submit(() -> { start.await(); assertThrows(BusinessException.class,
                    () -> service.bindEmail(1L,"old-pass","safe@example.com",id,wrong)); return null; }));
            start.countDown(); for(var job:jobs) job.get(10,TimeUnit.SECONDS);
            assertEquals(5,db.queryForObject("SELECT attempts FROM recovery_code WHERE request_id=?",Integer.class,id));
        } finally { pool.shutdownNow(); }
    }
    @Test void queueRejectionAndAsyncMailFailureKeepTheUniformResponseAndNoActiveCode() {
        bind("safe@example.com"); delivered.clear(); rejectQueue=true;
        var rejected=service.requestResetCode("student01","safe@example.com","ip-reset");
        assertEquals(0,queue.size()); assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE purpose='RESET'",Integer.class));
        ageRateEvents(61); rejectQueue=false; failMail=true;
        var failed=service.requestResetCode("student01","safe@example.com","ip-reset");
        assertEquals(rejected.message(),failed.message()); queue.removeFirst().run();
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE purpose='RESET' AND send_status='ACTIVE'",Integer.class));
        assertTrue(delivered.isEmpty());
    }
    @Test void aDisabledVerifiedAccountGetsTheSameResponseWithoutDelivery() {
        bind("safe@example.com"); delivered.clear(); db.update("UPDATE user SET status=0 WHERE user_id=1");
        var disabled=service.requestResetCode("student01","safe@example.com","ip-reset");
        var absent=service.requestResetCode("unknown01","safe@example.com","ip-other");
        assertEquals(absent.message(),disabled.message()); queue.forEach(Runnable::run);
        assertTrue(delivered.isEmpty());
    }

    @Test void aResetQueuedBeforePasswordChangeCannotCreateANewVersionCode() {
        bind("safe@example.com"); delivered.clear();
        String id=service.requestResetCode("student01","safe@example.com","ip-reset").requestId();
        service.changePassword(1L,"old-pass","new-pass");
        queue.removeFirst().run();
        assertTrue(delivered.isEmpty(),"A task accepted with old credentials must not send a fresh code");
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM recovery_code WHERE request_id=?",Integer.class,id));
        assertEquals(1,users.selectById(1L).getCredentialVersion());
    }
}

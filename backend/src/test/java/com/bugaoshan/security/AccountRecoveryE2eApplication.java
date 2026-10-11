package com.bugaoshan.security;

import com.bugaoshan.BugaoshanApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.util.Base64;

/** Starts the production application only against the runner's guarded disposable database. */
public final class AccountRecoveryE2eApplication {
    private AccountRecoveryE2eApplication() {}

    public static void main(String[] args) throws Exception {
        String nonce = required("COURSEHUB_RECOVERY_MYSQL_NONCE");
        String url = required("COURSEHUB_RECOVERY_MYSQL_URL");
        String user = required("COURSEHUB_RECOVERY_MYSQL_USER");
        String password = System.getenv("COURSEHUB_RECOVERY_MYSQL_PASSWORD");
        if (password == null) password = ""; // Windows PowerShell 5 removes empty environment values.
        require(nonce.matches("[0-9a-f]{32}") && "root".equals(user) && password.isEmpty(),
                "Only a new password-free disposable instance is supported");
        require(url.startsWith("jdbc:mysql://"), "Expected a MySQL JDBC URL");
        URI uri = URI.create(url.substring("jdbc:".length()));
        require("127.0.0.1".equals(uri.getHost()) && uri.getPort() >= 1024
                        && uri.getPort() <= 65535 && uri.getUserInfo() == null && uri.getFragment() == null
                        && uri.getPath().matches("/coursehub_recovery_check_[0-9a-f]{32}"),
                "Refusing an application database or non-loopback server");
        int apiPort = port("COURSEHUB_RECOVERY_E2E_PORT");
        int smtpPort = port("COURSEHUB_RECOVERY_E2E_SMTP_PORT");
        require(apiPort != smtpPort && apiPort != uri.getPort() && smtpPort != uri.getPort(),
                "Database, API and SMTP ports must differ");
        String runId = uri.getPath().substring("/coursehub_recovery_check_".length());
        Path evidence = Path.of(required("COURSEHUB_RECOVERY_E2E_EVIDENCE_PATH")).toRealPath();
        require(Files.isDirectory(evidence) && !Files.isSymbolicLink(evidence)
                        && evidence.getFileName().toString().equals("coursehub-recovery-mysql-" + runId)
                        && evidence.getParent().getFileName().toString().equals("target")
                        && evidence.getParent().getParent().getFileName().toString().equals("backend"),
                "Refusing an unrelated evidence directory");

        // Do not create fixtures until both the URL and this run's SQL ownership marker are verified.
        try (var connection = DriverManager.getConnection(url, user, password)) {
            require(connection.getMetaData().getDatabaseMajorVersion() == 8, "MySQL 8 is required");
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT nonce FROM recovery_test_guard")) {
                require(rows.next() && nonce.equals(rows.getString(1)) && !rows.next(),
                        "Disposable database ownership marker does not match");
            }
            try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM user WHERE username=?")) {
                statement.setString(1, "e2e_student");
                try (var rows = statement.executeQuery()) {
                    require(rows.next() && rows.getInt(1) == 0, "E2E requires a fresh fixture");
                }
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO user(username,password_hash,nickname,email,user_type,status,credential_version)"
                            + " VALUES(?,?,?,?,1,1,0)")) {
                statement.setString(1, "e2e_student");
                statement.setString(2, new BCryptPasswordEncoder().encode("E2e-old-123"));
                statement.setString(3, "E2E测试学生");
                statement.setString(4, "contact@example.test");
                require(statement.executeUpdate() == 1, "Fixture insertion failed");
            }
        }

        // Programmatic command-line properties outrank inherited environment/application configuration.
        // Keys stay in memory, and SMTP always targets the local receiver without authentication or TLS.
        SpringApplication.run(BugaoshanApplication.class,
                "--server.address=127.0.0.1", "--server.port=" + apiPort,
                "--spring.datasource.url=" + url, "--spring.datasource.username=root",
                "--spring.datasource.password=", "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.datasource.hikari.connection-timeout=5000",
                "--spring.mail.host=127.0.0.1", "--spring.mail.port=" + smtpPort,
                "--spring.mail.username=", "--spring.mail.password=", "--spring.mail.default-encoding=UTF-8",
                "--spring.mail.properties.mail.smtp.auth=false",
                "--spring.mail.properties.mail.smtp.starttls.enable=false",
                "--spring.mail.properties.mail.smtp.starttls.required=false",
                "--spring.mail.properties.mail.smtp.ssl.enable=false",
                "--spring.mail.properties.mail.debug=false",
                "--spring.mail.properties.mail.smtp.connectiontimeout=5000",
                "--spring.mail.properties.mail.smtp.timeout=5000",
                "--spring.mail.properties.mail.smtp.writetimeout=5000",
                "--recovery.enabled=true", "--recovery.from=local@example.test",
                "--recovery.hmac-secret=" + randomKey(), "--jwt.secret=" + randomKey(),
                "--jwt.expiration=86400000", "--jwt.refresh-expiration=604800000",
                "--file.upload.upload-path=" + evidence.resolve("runtime-uploads"),
                "--sms.mock=false", "--spring.main.banner-mode=off",
                "--logging.level.root=WARN", "--logging.level.com.bugaoshan=WARN",
                "--debug=false", "--trace=false");
        System.out.println("E2E production backend ready on loopback port " + apiPort);
    }

    private static String randomKey() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static int port(String name) {
        int value = Integer.parseInt(required(name));
        require(value >= 1024 && value <= 65535, "Invalid local test port");
        return value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(), "Missing runner variable " + name);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

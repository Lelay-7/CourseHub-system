-- Apply once to an existing database after backup. Do not run db/init.sql again.
ALTER TABLE user ADD COLUMN recovery_email VARCHAR(254) NULL;
ALTER TABLE user ADD COLUMN recovery_email_verified_at DATETIME(6) NULL;
ALTER TABLE user ADD COLUMN credential_version INT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uk_user_recovery_email ON user(recovery_email);

CREATE TABLE recovery_code (
    request_id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(8) NOT NULL,
    email VARCHAR(254) NOT NULL,
    credential_version INT NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    send_status VARCHAR(10) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_recovery_user_purpose ON recovery_code(user_id, purpose);
CREATE INDEX idx_recovery_expiry ON recovery_code(expires_at);

-- Lock rows serialize rolling-window checks across application instances.
-- Keys contain only SHA-256 digests, not usernames, email addresses or IPs.
CREATE TABLE recovery_rate_lock (scope_key CHAR(64) PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE recovery_rate_event (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope_key CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_recovery_rate_scope ON recovery_rate_event(scope_key, created_at);

CREATE TABLE user (
 user_id BIGINT PRIMARY KEY, username VARCHAR(100), password_hash VARCHAR(100), nickname VARCHAR(100),
 avatar_url VARCHAR(500), phone VARCHAR(30), email VARCHAR(254), recovery_email VARCHAR(254),
 real_name VARCHAR(100), student_no VARCHAR(100), department VARCHAR(100), user_type INT DEFAULT 1,
 credential_version INT DEFAULT 0, status INT DEFAULT 1, is_deleted INT DEFAULT 0, update_time DATETIME
);
CREATE TABLE friend (
 friend_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, target_id BIGINT NOT NULL,
 status INT NOT NULL DEFAULT 0, create_time DATETIME, update_time DATETIME,
 user_low_id BIGINT GENERATED ALWAYS AS (LEAST(user_id,target_id)),
 user_high_id BIGINT GENERATED ALWAYS AS (GREATEST(user_id,target_id)),
 UNIQUE(user_low_id,user_high_id)
);

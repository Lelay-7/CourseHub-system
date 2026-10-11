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

CREATE TABLE IF NOT EXISTS chat_conversation (
 conversation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 conversation_type VARCHAR(20) NOT NULL,
 private_user_low_id BIGINT NOT NULL, private_user_high_id BIGINT NOT NULL,
 last_sequence BIGINT NOT NULL DEFAULT 0, last_message_at DATETIME(3),
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_chat_private_pair(conversation_type,private_user_low_id,private_user_high_id)
);
CREATE TABLE IF NOT EXISTS chat_conversation_member (
 conversation_id BIGINT NOT NULL,user_id BIGINT NOT NULL,last_read_sequence BIGINT NOT NULL DEFAULT 0,
 joined_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(conversation_id,user_id), INDEX idx_chat_member_user(user_id,conversation_id)
);
CREATE TABLE IF NOT EXISTS chat_message (
 message_id BIGINT AUTO_INCREMENT PRIMARY KEY,conversation_id BIGINT NOT NULL,sequence BIGINT NOT NULL,
 sender_id BIGINT NOT NULL,client_message_id CHAR(36) NOT NULL,message_type VARCHAR(20) NOT NULL,
 content VARCHAR(20000) NOT NULL,created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_chat_sequence(conversation_id,sequence),UNIQUE KEY uk_chat_client(sender_id,client_message_id),
 INDEX idx_chat_history(conversation_id,sequence)
);

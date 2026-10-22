CREATE TABLE user (
 user_id BIGINT PRIMARY KEY, username VARCHAR(100), nickname VARCHAR(100), user_type INT,
 status INT DEFAULT 1, is_deleted INT DEFAULT 0, score_balance INT DEFAULT 0, total_score INT DEFAULT 0,
 update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE course(course_id BIGINT PRIMARY KEY,course_name VARCHAR(200),status INT,is_deleted INT DEFAULT 0);
CREATE TABLE material (
 material_id BIGINT AUTO_INCREMENT PRIMARY KEY,material_name VARCHAR(200),material_type INT,course_id BIGINT,
 uploader_id BIGINT,file_url VARCHAR(500),storage_key VARCHAR(255),original_file_name VARCHAR(255),content_type VARCHAR(100),
 file_size BIGINT,download_score INT,download_count INT,description VARCHAR(500),reject_reason VARCHAR(500),keyword VARCHAR(100),
 status INT,revision INT NOT NULL DEFAULT 1,upload_rewarded INT NOT NULL DEFAULT 0,
 create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,is_deleted INT DEFAULT 0
);
CREATE TABLE material_revision (
 material_id BIGINT NOT NULL,revision INT NOT NULL,material_name VARCHAR(200) NOT NULL,material_type INT NOT NULL,
 course_id BIGINT,uploader_id BIGINT NOT NULL,file_url VARCHAR(500),storage_key VARCHAR(255),original_file_name VARCHAR(255),
 content_type VARCHAR(100),file_size BIGINT,download_score INT,description VARCHAR(500),keyword VARCHAR(100),
 status INT NOT NULL,reject_reason VARCHAR(500),create_time TIMESTAMP NOT NULL,PRIMARY KEY(material_id,revision)
);
CREATE TABLE material_audit_log (
 audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,material_id BIGINT NOT NULL,revision INT NOT NULL,
 reviewer_id BIGINT NOT NULL,action VARCHAR(20) NOT NULL,reason VARCHAR(500),create_time TIMESTAMP NOT NULL
);
CREATE TABLE score_record (
 record_id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,change_type INT,change_amount INT,balance_after INT,
 source_desc VARCHAR(200),related_id BIGINT,create_time TIMESTAMP,update_time TIMESTAMP
);
CREATE TABLE favorite(favorite_id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,target_type INT,target_id BIGINT,create_time TIMESTAMP);
CREATE TABLE material_download(download_id BIGINT AUTO_INCREMENT PRIMARY KEY,material_id BIGINT,user_id BIGINT,score_cost INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(material_id,user_id));
CREATE TABLE material_report(report_id BIGINT AUTO_INCREMENT PRIMARY KEY,material_id BIGINT,reporter_id BIGINT,reason VARCHAR(500),status INT,create_time TIMESTAMP);

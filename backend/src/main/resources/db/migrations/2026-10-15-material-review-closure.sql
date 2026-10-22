-- Apply once to an existing database after backup. New databases use the matching db/init.sql schema.
-- Do not rerun init.sql against an existing database. This migration does not reset status, balance or score history.
ALTER TABLE material ADD COLUMN revision INT NOT NULL DEFAULT 1;
ALTER TABLE material ADD COLUMN upload_rewarded TINYINT NOT NULL DEFAULT 0;

CREATE TABLE material_revision (
    material_id BIGINT NOT NULL,
    revision INT NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    material_type TINYINT NOT NULL,
    course_id BIGINT,
    uploader_id BIGINT NOT NULL,
    file_url VARCHAR(500),
    storage_key VARCHAR(255),
    original_file_name VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    download_score INT DEFAULT 0,
    description VARCHAR(500),
    keyword VARCHAR(100),
    status TINYINT NOT NULL,
    reject_reason VARCHAR(500),
    create_time DATETIME NOT NULL,
    PRIMARY KEY (material_id, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE material_audit_log (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    revision INT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    create_time DATETIME NOT NULL,
    INDEX idx_material_audit (material_id, revision, audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Published and deactivated legacy uploads have already passed review. Recorded rewards are also respected.
UPDATE material m SET upload_rewarded=1
WHERE m.status IN (1,3) OR EXISTS (
    SELECT 1 FROM score_record s WHERE s.related_id=m.material_id AND s.user_id=m.uploader_id
      AND s.change_type=1 AND s.source_desc LIKE '上传资料审核通过：%'
);

INSERT INTO material_revision(material_id,revision,material_name,material_type,course_id,uploader_id,
    file_url,storage_key,original_file_name,content_type,file_size,download_score,description,keyword,status,reject_reason,create_time)
SELECT material_id,revision,material_name,material_type,course_id,uploader_id,
    file_url,storage_key,original_file_name,content_type,file_size,download_score,description,keyword,status,reject_reason,
    COALESCE(update_time,create_time,NOW()) FROM material;

-- Old reviews have no trustworthy reviewer identity/time, so no fabricated audit rows are inserted.
-- Keep current and historical files. Rollback requires restoring the pre-migration database backup;
-- do not remove a stored file while any material_revision still references it.

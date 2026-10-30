-- Apply once to an existing database after backup; fresh databases use db/init.sql.
-- Existing order status numbers and balances are preserved.
ALTER TABLE material_report
    ADD COLUMN resolution_action VARCHAR(20),
    ADD COLUMN resolution_reason VARCHAR(500),
    ADD COLUMN handled_by BIGINT,
    ADD COLUMN handled_time DATETIME;
ALTER TABLE `order` ADD COLUMN fulfillment_note VARCHAR(1000);
ALTER TABLE square_post ADD COLUMN course_id BIGINT;
CREATE INDEX idx_square_course ON square_post(course_id, status, is_deleted);

-- Preserve legacy display text. Only unambiguous, active course names get an ID.
UPDATE square_post p JOIN (
    SELECT course_name, MIN(course_id) AS course_id FROM course
    WHERE is_deleted=0 AND status=1 GROUP BY course_name HAVING COUNT(*)=1
) c ON p.course_name=c.course_name
SET p.course_id=c.course_id WHERE p.course_id IS NULL;

-- Legacy handled reports/orders lack reliable actor, time or delivery evidence.
-- Keep these new fields NULL; the UI labels the missing historical explanation.
-- Rollback requires the pre-migration backup. Do not reset status or score balances.

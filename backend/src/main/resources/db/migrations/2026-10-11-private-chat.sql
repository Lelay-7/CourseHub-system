-- Run with mysql client. Back up friend/user before running: DDL implicitly commits.
-- Preflight runs before any business-table mutation or ALTER. Invalid data requires manual repair.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_private_chat$$
CREATE PROCEDURE migrate_private_chat()
BEGIN
    DECLARE removed BIGINT DEFAULT 0;
    IF EXISTS (SELECT 1 FROM friend f
        LEFT JOIN user a ON a.user_id=f.user_id LEFT JOIN user b ON b.user_id=f.target_id
        WHERE f.user_id=f.target_id OR a.user_id IS NULL OR b.user_id IS NULL
           OR f.status IS NULL OR f.status NOT IN (0,1)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid friend data: repair self/orphan/status rows before migration';
    END IF;
    -- Accepted wins; otherwise lowest ID. Disabled/soft-deleted accounts remain valid historical participants.
    DELETE loser FROM friend loser JOIN friend winner
      ON LEAST(loser.user_id,loser.target_id)=LEAST(winner.user_id,winner.target_id)
     AND GREATEST(loser.user_id,loser.target_id)=GREATEST(winner.user_id,winner.target_id)
     AND (winner.status>loser.status OR (winner.status=loser.status AND winner.friend_id<loser.friend_id));
    SET removed=ROW_COUNT();
    IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='friend' AND column_name='user_low_id') THEN
        ALTER TABLE friend ADD COLUMN user_low_id BIGINT GENERATED ALWAYS AS (LEAST(user_id,target_id)) STORED;
    END IF;
    IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='friend' AND column_name='user_high_id') THEN
        ALTER TABLE friend ADD COLUMN user_high_id BIGINT GENERATED ALWAYS AS (GREATEST(user_id,target_id)) STORED;
    END IF;
    IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='friend' AND index_name='uk_friend_pair') THEN
        ALTER TABLE friend ADD UNIQUE INDEX uk_friend_pair(user_low_id,user_high_id);
    END IF;
    SELECT removed AS removed_duplicate_friend_rows;
END$$
CALL migrate_private_chat()$$
DROP PROCEDURE migrate_private_chat$$
DELIMITER ;

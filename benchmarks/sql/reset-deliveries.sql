\pset pager off

TRUNCATE delivery_attempts;

UPDATE deliveries
SET status = 'PENDING',
    attempt_count = 0,
    next_attempt_at = now(),
    locked_at = NULL,
    lock_token = NULL,
    completed_at = NULL,
    updated_at = now();

SELECT count(*) AS pending
FROM deliveries
WHERE status = 'PENDING';

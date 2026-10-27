\pset pager off

SELECT status, count(*) AS deliveries
FROM deliveries
GROUP BY status
ORDER BY status;

SELECT
    count(*) AS total,
    count(*) FILTER (WHERE status = 'DELIVERED') AS delivered,
    count(*) FILTER (WHERE status = 'FAILED') AS failed,
    count(*) FILTER (WHERE status = 'PENDING') AS pending,
    count(*) FILTER (WHERE status = 'DELIVERING') AS delivering,
    max(attempt_count) AS max_attempts_used
FROM deliveries;

SELECT count(*) AS attempts
FROM delivery_attempts;

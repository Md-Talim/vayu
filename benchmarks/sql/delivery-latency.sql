\pset pager off

WITH delivered AS (
    SELECT
        d.id,
        d.created_at,
        d.completed_at,
        d.attempt_count,
        min(a.started_at) AS first_claimed_at
    FROM deliveries d
    JOIN delivery_attempts a ON a.delivery_id = d.id
    WHERE d.status = 'DELIVERED'
    GROUP BY d.id, d.created_at, d.completed_at, d.attempt_count
),
drain_window AS (
    SELECT
        min(first_claimed_at) AS first_claim,
        max(completed_at) AS last_completion,
        count(*) AS delivered
    FROM delivered
)
SELECT
    delivered,
    first_claim,
    last_completion,
    round(extract(epoch FROM (last_completion - first_claim))::numeric, 3) AS drain_seconds,
    round(
        delivered / nullif(extract(epoch FROM (last_completion - first_claim)), 0)::numeric,
        2
    ) AS deliveries_per_second
FROM drain_window;

SELECT
    round(
        percentile_cont(0.5) WITHIN GROUP (
            ORDER BY extract(epoch FROM (completed_at - created_at))
        )::numeric,
        3
    ) AS p50_seconds,
    round(
        percentile_cont(0.95) WITHIN GROUP (
            ORDER BY extract(epoch FROM (completed_at - created_at))
        )::numeric,
        3
    ) AS p95_seconds,
    round(
        percentile_cont(0.99) WITHIN GROUP (
            ORDER BY extract(epoch FROM (completed_at - created_at))
        )::numeric,
        3
    ) AS p99_seconds,
    round(
        min(extract(epoch FROM (completed_at - created_at)))::numeric,
        3
    ) AS min_seconds,
    round(
        max(extract(epoch FROM (completed_at - created_at)))::numeric,
        3
    ) AS max_seconds
FROM deliveries
WHERE status = 'DELIVERED';

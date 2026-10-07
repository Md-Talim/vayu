-- V1: vayu core schema

CREATE TABLE endpoints (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url         TEXT        NOT NULL,
    secret      TEXT        NOT NULL,   -- stored readable: needed to compute the HMAC signature
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type             VARCHAR(128) NOT NULL,
    payload          JSONB        NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_events_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id  UUID         NOT NULL REFERENCES endpoints (id) ON DELETE CASCADE,
    event_type   VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_subscription_endpoint_event UNIQUE (endpoint_id, event_type)
);

-- Fan-out lookup: "which endpoints want this event type?"
CREATE INDEX idx_subscriptions_event_type ON subscriptions (event_type, endpoint_id);

CREATE TABLE deliveries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         UUID        NOT NULL REFERENCES events (id),
    subscription_id  UUID        NOT NULL REFERENCES subscriptions (id),

    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count    INTEGER     NOT NULL DEFAULT 0,
    max_attempts     INTEGER     NOT NULL DEFAULT 10,

    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at        TIMESTAMPTZ,
    version          BIGINT      NOT NULL DEFAULT 0,   -- JPA @Version, fences stale workers

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,

    CONSTRAINT uq_deliveries_event_subscription UNIQUE (event_id, subscription_id),

    CONSTRAINT chk_deliveries_status
        CHECK (status IN ('PENDING', 'DELIVERING', 'DELIVERED', 'FAILED')),

    CONSTRAINT chk_deliveries_attempts
        CHECK (attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts),

    -- locked_at is set exactly while a worker holds the delivery
    CONSTRAINT chk_deliveries_lock
        CHECK ((status = 'DELIVERING') = (locked_at IS NOT NULL)),

    -- completed_at is set exactly when the delivery is in a terminal state
    CONSTRAINT chk_deliveries_completed
        CHECK ((status IN ('DELIVERED', 'FAILED')) = (completed_at IS NOT NULL))
);

-- Worker's hot query: due, still-pending deliveries.
CREATE INDEX idx_deliveries_due ON deliveries (next_attempt_at) WHERE status = 'PENDING';

-- Reaper's query: deliveries stuck in DELIVERING.
CREATE INDEX idx_deliveries_locked ON deliveries (locked_at) WHERE status = 'DELIVERING';

-- Postgres does not index FK columns automatically.
CREATE INDEX idx_deliveries_subscription ON deliveries (subscription_id);

CREATE TABLE delivery_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id     UUID        NOT NULL REFERENCES deliveries (id) ON DELETE CASCADE,

    attempt_number  INTEGER     NOT NULL,
    http_status     INTEGER,
    response_body   TEXT,                 -- truncated in code (e.g. first 1 KB), for debugging
    error           TEXT,

    started_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,          -- null if the process died mid-attempt

    CONSTRAINT uq_delivery_attempt_number UNIQUE (delivery_id, attempt_number)
);

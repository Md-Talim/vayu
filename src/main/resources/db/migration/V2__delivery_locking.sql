-- V2: lease token for safe delivery claiming

ALTER TABLE deliveries ADD COLUMN lock_token UUID;

ALTER TABLE deliveries DROP COLUMN version;

ALTER TABLE deliveries ALTER COLUMN next_attempt_at DROP NOT NULL;

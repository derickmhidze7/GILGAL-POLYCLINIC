-- V10: Create refresh_tokens table

CREATE TABLE refresh_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    user_id     UUID         NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(256) NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMP
);

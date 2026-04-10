-- V2: Create app_users table

CREATE TABLE app_users (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    username            VARCHAR(60)  NOT NULL UNIQUE,
    email               VARCHAR(150) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    role                VARCHAR(30)  NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    account_non_locked  BOOLEAN      NOT NULL DEFAULT TRUE,
    staff_id            UUID REFERENCES staff(id) ON DELETE SET NULL
);

-- Seed default admin user
-- Password: Admin@1234  (BCrypt 12 rounds)
INSERT INTO app_users (username, email, password_hash, role)
VALUES (
    'admin',
    'admin@adags.hospital',
    '$2b$12$4Uo6bxNcYk0pqNBouNk0C.4AGKSvrm8UgkifUvExrIFuDLUeZ8DNi',
    'ADMIN'
);

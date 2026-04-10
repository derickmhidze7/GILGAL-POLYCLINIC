-- V30: Add cancellation request fields to invoices table

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS cancellation_reason        TEXT,
    ADD COLUMN IF NOT EXISTS cancellation_requested_by  UUID REFERENCES app_users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS cancellation_requested_at  TIMESTAMP WITHOUT TIME ZONE;

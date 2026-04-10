-- V23: Ward pricing — add daily rate and ward invoice tracking to ward_patient_assignments

ALTER TABLE ward_patient_assignments
    ADD COLUMN IF NOT EXISTS ward_daily_rate  NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS ward_invoice_id  UUID;

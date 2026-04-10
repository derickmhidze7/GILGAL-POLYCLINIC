-- ============================================================
-- V17 : Lab Technician portal enhancements
-- ============================================================

-- 1. Sample-tracking fields on lab_requests
ALTER TABLE lab_requests
    ADD COLUMN sample_type          VARCHAR(50),
    ADD COLUMN sample_collected_at  TIMESTAMP,
    ADD COLUMN sample_received_at   TIMESTAMP,
    ADD COLUMN sample_quality       VARCHAR(20) NOT NULL DEFAULT 'ADEQUATE';

-- 2. Verification / submission fields on lab_results
ALTER TABLE lab_results
    ADD COLUMN verified_by_id  UUID REFERENCES staff(id) ON DELETE SET NULL,
    ADD COLUMN verified_at     TIMESTAMP,
    ADD COLUMN is_submitted    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_locked       BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Multi-parameter sub-results table
CREATE TABLE lab_result_parameters (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    lab_result_id   UUID         NOT NULL REFERENCES lab_results(id) ON DELETE CASCADE,
    parameter_name  VARCHAR(100) NOT NULL,
    result_value    VARCHAR(200),
    unit            VARCHAR(50),
    reference_range VARCHAR(100),
    interpretation  VARCHAR(15)
);

CREATE INDEX idx_lab_result_parameters_result ON lab_result_parameters(lab_result_id);

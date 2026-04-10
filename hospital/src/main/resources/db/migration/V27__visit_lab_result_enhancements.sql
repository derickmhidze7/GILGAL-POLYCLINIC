-- V27: Extend visit_lab_requests with full result fields
-- and add visit_lab_result_parameters table

-- ── 1. Extend visit_lab_requests ────────────────────────────────────────────
ALTER TABLE visit_lab_requests
    ADD COLUMN IF NOT EXISTS sample_type          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS sample_collected_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sample_quality       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS sample_quality_note  TEXT,
    ADD COLUMN IF NOT EXISTS methodology          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reagents_used        TEXT,
    ADD COLUMN IF NOT EXISTS findings             TEXT,
    ADD COLUMN IF NOT EXISTS interpretation_text  TEXT,
    ADD COLUMN IF NOT EXISTS reference_range_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS conclusion           VARCHAR(255);

-- ── 2. Create visit_lab_result_parameters ───────────────────────────────────
CREATE TABLE IF NOT EXISTS visit_lab_result_parameters (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),

    visit_lab_request_id UUID     NOT NULL,
    parameter_name   VARCHAR(100) NOT NULL,
    result_value     VARCHAR(200),
    unit             VARCHAR(50),
    reference_range  VARCHAR(100),
    flag             VARCHAR(10),
    method           VARCHAR(100),
    sort_order       INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT fk_vlrp_visit_lab_request
        FOREIGN KEY (visit_lab_request_id)
        REFERENCES visit_lab_requests(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_vlrp_visit_lab_request_id
    ON visit_lab_result_parameters(visit_lab_request_id);

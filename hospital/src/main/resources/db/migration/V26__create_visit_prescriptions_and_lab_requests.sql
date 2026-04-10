-- ================================================================
--  V26 — New Doctor Visit Prescription & Lab Request System
--  These are NEW tables alongside the existing prescriptions /
--  lab_requests tables which remain untouched for backward compat.
--  Source of truth: service_price_items
--    type = 'PHARMACY'    → visit_prescriptions
--    type = 'LABORATORY'  → visit_lab_requests
-- ================================================================

-- ---------------------------------------------------------------
-- visit_prescriptions
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS visit_prescriptions (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    medical_record_id UUID         NOT NULL REFERENCES medical_records(id) ON DELETE CASCADE,
    price_item_id     UUID         NOT NULL REFERENCES service_price_items(id),
    medication_name   VARCHAR(255) NOT NULL,
    dosage            VARCHAR(100),
    frequency         VARCHAR(100),
    duration          VARCHAR(100),
    route             VARCHAR(100),
    instructions      TEXT,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING_DISPENSING',
    dispensed_qty     INTEGER,
    dispensed_at      TIMESTAMP,
    created_by_id     UUID,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_visit_prescriptions PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_vprx_record ON visit_prescriptions (medical_record_id);
CREATE INDEX IF NOT EXISTS idx_vprx_status ON visit_prescriptions (status);

-- ---------------------------------------------------------------
-- visit_lab_requests
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS visit_lab_requests (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    medical_record_id   UUID         NOT NULL REFERENCES medical_records(id) ON DELETE CASCADE,
    price_item_id       UUID         NOT NULL REFERENCES service_price_items(id),
    test_name           VARCHAR(255) NOT NULL,
    urgency             VARCHAR(30)  NOT NULL DEFAULT 'ROUTINE',
    clinical_notes      TEXT,
    special_instructions TEXT,
    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    result_summary      TEXT,
    completed_at        TIMESTAMP,
    created_by_id       UUID,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_visit_lab_requests PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_vlr_record ON visit_lab_requests (medical_record_id);
CREATE INDEX IF NOT EXISTS idx_vlr_status ON visit_lab_requests (status);

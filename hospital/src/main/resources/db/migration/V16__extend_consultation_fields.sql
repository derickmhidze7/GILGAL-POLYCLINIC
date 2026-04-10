-- V16: Extend medical_records, prescriptions, and lab_requests with full consultation fields

-- ── History & Examination + Diagnosis + Disposition on medical_records ─────
ALTER TABLE medical_records
    ADD COLUMN IF NOT EXISTS chief_complaints                TEXT,
    ADD COLUMN IF NOT EXISTS history_of_presenting_illness   TEXT,
    ADD COLUMN IF NOT EXISTS past_medical_history            TEXT,
    ADD COLUMN IF NOT EXISTS past_surgical_history           TEXT,
    ADD COLUMN IF NOT EXISTS family_social_history           TEXT,
    ADD COLUMN IF NOT EXISTS drug_food_allergies             TEXT,
    ADD COLUMN IF NOT EXISTS physical_examination            TEXT,
    ADD COLUMN IF NOT EXISTS provisional_diagnosis           VARCHAR(500),
    ADD COLUMN IF NOT EXISTS final_diagnosis                 VARCHAR(500),
    ADD COLUMN IF NOT EXISTS treatment_plan                  TEXT,
    ADD COLUMN IF NOT EXISTS consultation_status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS next_step                       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS follow_up_instructions          TEXT,
    ADD COLUMN IF NOT EXISTS forwarded_to_doctor_id          UUID REFERENCES staff(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS forwarded_type                  VARCHAR(20);

-- ── Route of administration on prescriptions ─────────────────────────────
ALTER TABLE prescriptions
    ADD COLUMN IF NOT EXISTS route VARCHAR(50);

-- ── Special instructions + service catalogue link on lab_requests ────────
ALTER TABLE lab_requests
    ADD COLUMN IF NOT EXISTS special_instructions   TEXT,
    ADD COLUMN IF NOT EXISTS service_price_item_id  UUID REFERENCES service_price_items(id) ON DELETE SET NULL;

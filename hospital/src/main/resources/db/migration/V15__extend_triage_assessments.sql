-- ================================================================
--  V15 — Extend triage_assessments
--  Adds: medical history, pain assessment, risk assessment,
--        infectious disease risk, anthropometric BMI, and
--        patient referral / consultation invoice tracking.
-- ================================================================

-- Medical History
ALTER TABLE triage_assessments
    ADD COLUMN IF NOT EXISTS known_allergies       TEXT,
    ADD COLUMN IF NOT EXISTS comorbidities         TEXT,
    ADD COLUMN IF NOT EXISTS current_symptoms      TEXT,
    ADD COLUMN IF NOT EXISTS mode_of_ambulation    VARCHAR(30);

-- Anthropometric
ALTER TABLE triage_assessments
    ADD COLUMN IF NOT EXISTS bmi NUMERIC(5,2);

-- Pain Assessment
ALTER TABLE triage_assessments
    ADD COLUMN IF NOT EXISTS has_pain       BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pain_score     INTEGER,
    ADD COLUMN IF NOT EXISTS pain_location  VARCHAR(255);

-- Risk Assessment
ALTER TABLE triage_assessments
    ADD COLUMN IF NOT EXISTS fall_risk   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fall_score  INTEGER;

-- Infectious Disease Risk
ALTER TABLE triage_assessments
    ADD COLUMN IF NOT EXISTS infectious_disease_risk BOOLEAN NOT NULL DEFAULT FALSE;

-- Patient Referral
ALTER TABLE triage_assessments
    ADD COLUMN IF NOT EXISTS referred_doctor_id      UUID REFERENCES staff(id),
    ADD COLUMN IF NOT EXISTS referral_type           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS consultation_invoice_id UUID REFERENCES invoices(id);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ta_referred_doctor ON triage_assessments (referred_doctor_id);
CREATE INDEX IF NOT EXISTS idx_ta_invoice         ON triage_assessments (consultation_invoice_id);

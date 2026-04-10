-- V20: Ward Nurse Module
-- Tables: ward_patient_assignments, vital_signs,
--         medication_administration_records, wound_care_notes
-- All tables extend BaseEntity (id, created_at, updated_at, created_by)

-- ----------------------------------------------------------------
-- 1. Ward Patient Assignments
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ward_patient_assignments (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id            UUID        NOT NULL REFERENCES patients(id),
    assigned_nurse_id     UUID        REFERENCES staff(id),
    assigned_by_doctor_id UUID        REFERENCES staff(id),
    appointment_id        UUID        REFERENCES appointments(id),
    status                VARCHAR(30) NOT NULL DEFAULT 'ADMITTED',
    ward                  VARCHAR(100),
    bed_number            VARCHAR(20),
    admit_date            TIMESTAMP   NOT NULL DEFAULT NOW(),
    discharge_date        TIMESTAMP,
    admission_notes       TEXT,
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_ward_assignments_patient ON ward_patient_assignments(patient_id);
CREATE INDEX IF NOT EXISTS idx_ward_assignments_nurse   ON ward_patient_assignments(assigned_nurse_id);
CREATE INDEX IF NOT EXISTS idx_ward_assignments_status  ON ward_patient_assignments(status);

-- ----------------------------------------------------------------
-- 2. Vital Signs
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vital_signs (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ward_assignment_id UUID        NOT NULL REFERENCES ward_patient_assignments(id),
    patient_id         UUID        NOT NULL REFERENCES patients(id),
    recorded_by_id     UUID        REFERENCES staff(id),
    recorded_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    bp_systolic        INTEGER,
    bp_diastolic       INTEGER,
    pulse_rate         INTEGER,
    temperature        NUMERIC(4,1),
    respiratory_rate   INTEGER,
    spo2               INTEGER,
    pain_score         INTEGER CHECK (pain_score BETWEEN 0 AND 10),
    has_alerts         BOOLEAN     NOT NULL DEFAULT FALSE,
    alert_details      TEXT,
    notes              TEXT,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_vital_signs_assignment  ON vital_signs(ward_assignment_id);
CREATE INDEX IF NOT EXISTS idx_vital_signs_recorded_at ON vital_signs(recorded_at DESC);

-- ----------------------------------------------------------------
-- 3. Medication Administration Records (MAR)
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medication_administration_records (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ward_assignment_id UUID        NOT NULL REFERENCES ward_patient_assignments(id),
    patient_id         UUID        NOT NULL REFERENCES patients(id),
    administered_by_id UUID        REFERENCES staff(id),
    medication_name    VARCHAR(200) NOT NULL,
    scheduled_time     TIMESTAMP   NOT NULL,
    administered_at    TIMESTAMP,
    dose_given         VARCHAR(100),
    route              VARCHAR(50),
    was_given          BOOLEAN     NOT NULL DEFAULT FALSE,
    skip_reason        VARCHAR(50),
    skip_notes         TEXT,
    remarks            TEXT,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_mar_assignment ON medication_administration_records(ward_assignment_id);
CREATE INDEX IF NOT EXISTS idx_mar_scheduled  ON medication_administration_records(scheduled_time);

-- ----------------------------------------------------------------
-- 4. Wound Care Notes
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wound_care_notes (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ward_assignment_id    UUID        NOT NULL REFERENCES ward_patient_assignments(id),
    patient_id            UUID        NOT NULL REFERENCES patients(id),
    recorded_by_id        UUID        REFERENCES staff(id),
    recorded_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    wound_appearance      TEXT,
    dressing_changed      BOOLEAN     NOT NULL DEFAULT FALSE,
    dressing_type         VARCHAR(100),
    signs_of_infection    BOOLEAN     NOT NULL DEFAULT FALSE,
    infection_description TEXT,
    remarks               TEXT,
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_wound_care_assignment ON wound_care_notes(ward_assignment_id);

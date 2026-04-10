-- ============================================================
-- V22 — Surgery & Procedure Module
-- ============================================================

-- ── Surgery Orders ──────────────────────────────────────────
CREATE TABLE surgery_orders (
    id                        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id                UUID         NOT NULL REFERENCES patients(id),
    requesting_doctor_id      UUID         NOT NULL REFERENCES staff(id),
    medical_record_id         UUID         REFERENCES medical_records(id),
    procedure_name            VARCHAR(255) NOT NULL,
    surgery_type              VARCHAR(60)  NOT NULL DEFAULT 'GENERAL',
    urgency                   VARCHAR(30)  NOT NULL DEFAULT 'ELECTIVE',
    anesthesia_type           VARCHAR(50),
    scheduled_date            TIMESTAMP,
    operating_theater         VARCHAR(100),
    status                    VARCHAR(30)  NOT NULL DEFAULT 'SCHEDULED',
    consent_obtained          BOOLEAN      NOT NULL DEFAULT FALSE,
    preop_notes               TEXT,
    postop_notes              TEXT,
    estimated_duration_minutes INT,
    actual_duration_minutes   INT,
    price                     NUMERIC(12,2),
    service_price_item_id     UUID         REFERENCES service_price_items(id),
    consent_document_path     VARCHAR(500),
    created_at                TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT now(),
    created_by                VARCHAR(100)
);

-- ── Assigned Nurses ──────────────────────────────────────────
CREATE TABLE surgery_assigned_nurses (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    surgery_order_id UUID        NOT NULL REFERENCES surgery_orders(id) ON DELETE CASCADE,
    nurse_id         UUID        NOT NULL REFERENCES staff(id),
    nurse_role       VARCHAR(60) NOT NULL DEFAULT 'SCRUB_NURSE',
    UNIQUE (surgery_order_id, nurse_id),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    created_by  VARCHAR(100)
);

-- ── Pre-op / Post-op Item Lists ──────────────────────────────
CREATE TABLE surgery_item_lists (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    surgery_order_id UUID          NOT NULL REFERENCES surgery_orders(id) ON DELETE CASCADE,
    item_type        VARCHAR(20)   NOT NULL,   -- PRE_OP | POST_OP
    item_name        VARCHAR(255)  NOT NULL,
    quantity         INT           NOT NULL DEFAULT 1,
    price            NUMERIC(12,2) NOT NULL DEFAULT 0,
    dispensed        BOOLEAN       NOT NULL DEFAULT FALSE,
    pharmacy_notes   TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT now(),
    created_by       VARCHAR(100)
);

-- ── Intraoperative Record ────────────────────────────────────
CREATE TABLE surgery_intraoperative (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    surgery_order_id    UUID        NOT NULL UNIQUE REFERENCES surgery_orders(id) ON DELETE CASCADE,
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    lead_surgeon        VARCHAR(255),
    anesthesiologist    VARCHAR(255),
    blood_loss_ml       INT,
    fluids_given_ml     INT,
    complications       TEXT,
    intraop_notes       TEXT,
    anesthesia_notes    TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    created_by          VARCHAR(100)
);

-- ── Post-op Care Records ─────────────────────────────────────
CREATE TABLE surgery_postop_care (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    surgery_order_id    UUID          NOT NULL REFERENCES surgery_orders(id) ON DELETE CASCADE,
    nurse_id            UUID          REFERENCES staff(id),
    recorded_at         TIMESTAMP     NOT NULL DEFAULT now(),
    consciousness_level VARCHAR(40),
    blood_pressure      VARCHAR(20),
    pulse_rate          INT,
    spo2                NUMERIC(5,2),
    pain_score          INT,
    temperature         NUMERIC(5,2),
    recovery_notes      TEXT,
    next_step           VARCHAR(40),
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),
    created_by          VARCHAR(100)
);

-- ── Indexes ───────────────────────────────────────────────────
CREATE INDEX idx_surgery_orders_patient   ON surgery_orders(patient_id);
CREATE INDEX idx_surgery_orders_doctor    ON surgery_orders(requesting_doctor_id);
CREATE INDEX idx_surgery_orders_status    ON surgery_orders(status);
CREATE INDEX idx_surgery_orders_scheduled ON surgery_orders(scheduled_date);
CREATE INDEX idx_surgery_item_lists_order ON surgery_item_lists(surgery_order_id);

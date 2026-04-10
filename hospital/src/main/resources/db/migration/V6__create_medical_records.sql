-- V6: Create medical_records, diagnoses, and prescriptions tables

CREATE TABLE medical_records (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT now(),
    created_by           VARCHAR(100),
    patient_id           UUID         NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    appointment_id       UUID REFERENCES appointments(id) ON DELETE SET NULL,
    attending_doctor_id  UUID         NOT NULL REFERENCES staff(id) ON DELETE RESTRICT,
    visit_date           TIMESTAMP    NOT NULL DEFAULT now(),
    clinical_notes       TEXT,
    follow_up_date       DATE
);

CREATE TABLE diagnoses (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now(),
    created_by        VARCHAR(100),
    medical_record_id UUID         NOT NULL REFERENCES medical_records(id) ON DELETE CASCADE,
    icd_code          VARCHAR(20),
    description       VARCHAR(500) NOT NULL,
    diagnosis_type    VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY'
);

CREATE TABLE medications (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT now(),
    created_by   VARCHAR(100),
    generic_name VARCHAR(150) NOT NULL,
    brand_name   VARCHAR(150),
    category     VARCHAR(100),
    form         VARCHAR(20)  NOT NULL,
    strength     VARCHAR(50),
    description  VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE prescriptions (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now(),
    created_by        VARCHAR(100),
    medical_record_id UUID         NOT NULL REFERENCES medical_records(id) ON DELETE CASCADE,
    medication_id     UUID         NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    dosage            VARCHAR(100) NOT NULL,
    frequency         VARCHAR(100) NOT NULL,
    duration          VARCHAR(100) NOT NULL,
    instructions      VARCHAR(500),
    dispensed         BOOLEAN      NOT NULL DEFAULT FALSE
);

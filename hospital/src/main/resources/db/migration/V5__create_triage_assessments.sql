-- V5: Create triage_assessments table

CREATE TABLE triage_assessments (
    id                       UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at               TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP   NOT NULL DEFAULT now(),
    created_by               VARCHAR(100),
    appointment_id           UUID REFERENCES appointments(id) ON DELETE SET NULL,
    patient_id               UUID         NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    nurse_id                 UUID         NOT NULL REFERENCES staff(id) ON DELETE RESTRICT,
    assessment_date_time     TIMESTAMP    NOT NULL DEFAULT now(),
    chief_complaint          VARCHAR(500) NOT NULL,
    temperature              NUMERIC(5,2),
    blood_pressure_systolic  INTEGER,
    blood_pressure_diastolic INTEGER,
    pulse_rate               INTEGER,
    respiratory_rate         INTEGER,
    oxygen_saturation        NUMERIC(5,2),
    weight                   NUMERIC(6,2),
    height                   NUMERIC(5,2),
    triage_priority          VARCHAR(20)  NOT NULL,
    notes                    VARCHAR(2000)
);

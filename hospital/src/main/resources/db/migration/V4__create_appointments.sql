-- V4: Create appointments table

CREATE TABLE appointments (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT now(),
    created_by            VARCHAR(100),
    patient_id            UUID         NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    doctor_id             UUID REFERENCES staff(id) ON DELETE SET NULL,
    scheduled_date_time   TIMESTAMP    NOT NULL,
    appointment_type      VARCHAR(30)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    notes                 VARCHAR(1000),
    booked_by_id          UUID REFERENCES app_users(id) ON DELETE SET NULL,
    cancellation_reason   VARCHAR(500)
);

-- V7: Create lab_requests and lab_results tables

CREATE TABLE lab_requests (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT now(),
    created_by           VARCHAR(100),
    medical_record_id    UUID REFERENCES medical_records(id) ON DELETE SET NULL,
    patient_id           UUID         NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    requesting_doctor_id UUID         NOT NULL REFERENCES staff(id) ON DELETE RESTRICT,
    test_name            VARCHAR(200) NOT NULL,
    test_code            VARCHAR(50),
    urgency              VARCHAR(15)  NOT NULL DEFAULT 'ROUTINE',
    requested_at         TIMESTAMP    NOT NULL DEFAULT now(),
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE lab_results (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    lab_request_id  UUID         NOT NULL UNIQUE REFERENCES lab_requests(id) ON DELETE CASCADE,
    performed_by_id UUID         NOT NULL REFERENCES staff(id) ON DELETE RESTRICT,
    result_value    VARCHAR(500),
    reference_range VARCHAR(200),
    unit            VARCHAR(50),
    interpretation  VARCHAR(15)  NOT NULL,
    result_date_time TIMESTAMP   NOT NULL DEFAULT now(),
    notes           TEXT
);

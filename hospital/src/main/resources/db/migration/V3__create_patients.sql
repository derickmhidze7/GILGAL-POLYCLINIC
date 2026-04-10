-- V3: Create patients and related tables

CREATE TABLE next_of_kin (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT now(),
    created_by   VARCHAR(100),
    full_name    VARCHAR(150) NOT NULL,
    relationship VARCHAR(60)  NOT NULL,
    phone        VARCHAR(30)  NOT NULL,
    email        VARCHAR(150)
);

CREATE TABLE patients (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now(),
    created_by        VARCHAR(100),
    first_name        VARCHAR(80)  NOT NULL,
    last_name         VARCHAR(80)  NOT NULL,
    date_of_birth     DATE         NOT NULL,
    gender            VARCHAR(20)  NOT NULL,
    national_id       VARCHAR(50)  UNIQUE,
    phone             VARCHAR(30),
    email             VARCHAR(150),
    street            VARCHAR(255),
    city              VARCHAR(100),
    province          VARCHAR(100),
    country           VARCHAR(100),
    postal_code       VARCHAR(20),
    blood_group       VARCHAR(10),
    registration_date DATE         NOT NULL DEFAULT CURRENT_DATE,
    next_of_kin_id    UUID REFERENCES next_of_kin(id) ON DELETE SET NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE patient_allergies (
    patient_id UUID        NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    allergy    VARCHAR(255)
);

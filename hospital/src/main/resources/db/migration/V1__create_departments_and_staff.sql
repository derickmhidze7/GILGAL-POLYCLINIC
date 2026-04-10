-- V1: Create departments and staff tables

CREATE TABLE departments (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE staff (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now(),
    created_by        VARCHAR(100),
    first_name        VARCHAR(80)  NOT NULL,
    last_name         VARCHAR(80)  NOT NULL,
    date_of_birth     DATE,
    gender            VARCHAR(20),
    phone             VARCHAR(30),
    email             VARCHAR(150) NOT NULL UNIQUE,
    department_id     UUID REFERENCES departments(id) ON DELETE SET NULL,
    staff_role        VARCHAR(30),
    specialization    VARCHAR(150),
    license_number    VARCHAR(80),
    employment_date   DATE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed initial departments
INSERT INTO departments (name, description) VALUES
    ('Emergency', 'Emergency and casualty department'),
    ('General Medicine', 'General outpatient and inpatient medicine'),
    ('Surgery', 'Surgical procedures and post-op care'),
    ('Paediatrics', 'Children health services'),
    ('Obstetrics & Gynaecology', 'Maternal and reproductive health'),
    ('Laboratory', 'Diagnostic laboratory services'),
    ('Pharmacy', 'Medication dispensing and inventory'),
    ('Radiology', 'Imaging and diagnostic radiology'),
    ('Administration', 'Hospital administration and records');

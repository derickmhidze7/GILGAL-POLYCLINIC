-- V11: Add performance indexes

-- Patients
CREATE INDEX idx_patients_national_id  ON patients(national_id);
CREATE INDEX idx_patients_last_name    ON patients(last_name);
CREATE INDEX idx_patients_active       ON patients(active);

-- Staff
CREATE INDEX idx_staff_email           ON staff(email);
CREATE INDEX idx_staff_department      ON staff(department_id);
CREATE INDEX idx_staff_role            ON staff(staff_role);
CREATE INDEX idx_staff_active          ON staff(active);

-- Appointments
CREATE INDEX idx_appointments_patient          ON appointments(patient_id);
CREATE INDEX idx_appointments_doctor           ON appointments(doctor_id);
CREATE INDEX idx_appointments_scheduled_dt     ON appointments(scheduled_date_time);
CREATE INDEX idx_appointments_status           ON appointments(status);

-- Triage
CREATE INDEX idx_triage_patient        ON triage_assessments(patient_id);
CREATE INDEX idx_triage_priority       ON triage_assessments(triage_priority);

-- Medical Records
CREATE INDEX idx_medical_records_patient        ON medical_records(patient_id);
CREATE INDEX idx_medical_records_doctor         ON medical_records(attending_doctor_id);
CREATE INDEX idx_medical_records_visit_date     ON medical_records(visit_date);

-- Lab
CREATE INDEX idx_lab_requests_patient  ON lab_requests(patient_id);
CREATE INDEX idx_lab_requests_status   ON lab_requests(status);
CREATE INDEX idx_lab_requests_urgency  ON lab_requests(urgency);

-- Pharmacy / Inventory
CREATE INDEX idx_inventory_medication   ON inventory_items(medication_id);
CREATE INDEX idx_inventory_expiry       ON inventory_items(expiry_date);

-- Billing
CREATE INDEX idx_invoices_patient       ON invoices(patient_id);
CREATE INDEX idx_invoices_status        ON invoices(status);
CREATE INDEX idx_invoices_number        ON invoices(invoice_number);

-- Refresh Tokens
CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash    ON refresh_tokens(token_hash);

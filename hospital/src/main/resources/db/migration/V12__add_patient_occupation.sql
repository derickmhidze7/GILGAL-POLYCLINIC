-- V12: Add occupation column to patients
ALTER TABLE patients ADD COLUMN IF NOT EXISTS occupation VARCHAR(100);

-- V30: Add report PDF attachment path to lab_results
ALTER TABLE lab_results
    ADD COLUMN IF NOT EXISTS report_pdf_path VARCHAR(512);

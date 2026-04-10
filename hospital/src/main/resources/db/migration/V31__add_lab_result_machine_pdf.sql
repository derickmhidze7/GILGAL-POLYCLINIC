-- V31: Add machine output PDF path to lab_results
ALTER TABLE lab_results
    ADD COLUMN IF NOT EXISTS machine_pdf_path VARCHAR(512);

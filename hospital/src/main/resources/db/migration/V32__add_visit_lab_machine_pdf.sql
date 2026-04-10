-- V32: Add machine output PDF path to visit_lab_requests
ALTER TABLE visit_lab_requests
    ADD COLUMN IF NOT EXISTS machine_pdf_path VARCHAR(512);

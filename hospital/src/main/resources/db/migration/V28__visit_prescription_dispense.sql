-- V28 — Wire VisitPrescription dispenses into dispensed_items
-- Allow prescription_id to be nullable (visit dispenses have no legacy prescription row)
-- Add visit_prescription_id FK to track the new V26 VisitPrescription dispensed here.

ALTER TABLE dispensed_items
    ALTER COLUMN prescription_id DROP NOT NULL;

ALTER TABLE dispensed_items
    ADD COLUMN IF NOT EXISTS visit_prescription_id UUID
        REFERENCES visit_prescriptions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_di_visit_prescription ON dispensed_items (visit_prescription_id);

-- Add counselling_notes to visit_prescriptions for pharmacist notes at dispense time
ALTER TABLE visit_prescriptions
    ADD COLUMN IF NOT EXISTS counselling_notes TEXT;

-- Add dispensed_by_id to visit_prescriptions for audit
ALTER TABLE visit_prescriptions
    ADD COLUMN IF NOT EXISTS dispensed_by_id UUID;

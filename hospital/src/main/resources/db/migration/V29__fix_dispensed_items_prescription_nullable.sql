-- V29 — Drop NOT NULL from dispensed_items.prescription_id
-- Required so that V26 VisitPrescription dispenses (which have no legacy Prescription row)
-- can be saved without violating the constraint.

ALTER TABLE dispensed_items
    ALTER COLUMN prescription_id DROP NOT NULL;

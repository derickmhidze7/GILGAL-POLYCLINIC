-- ============================================================
-- V18 : Pharmacist portal enhancements
-- ============================================================

-- 1. Pharmacy workflow status + counselling notes on prescriptions
ALTER TABLE prescriptions
    ADD COLUMN pharmacy_status   VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN counselling_notes TEXT;

-- 2. Dispensing notes on dispensed_items
ALTER TABLE dispensed_items
    ADD COLUMN dispensing_notes VARCHAR(500);

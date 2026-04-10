-- ============================================================
-- V39 : Fix prescriptions pharmacy_status check constraint
--       to include AWAITING_PAYMENT (added to enum later)
-- ============================================================

ALTER TABLE prescriptions
    DROP CONSTRAINT IF EXISTS prescriptions_pharmacy_status_check;

ALTER TABLE prescriptions
    ADD CONSTRAINT prescriptions_pharmacy_status_check
        CHECK (pharmacy_status IN (
            'PENDING',
            'AWAITING_PAYMENT',
            'READY_TO_DISPENSE',
            'IN_PROGRESS',
            'PARTIALLY_DISPENSED',
            'DISPENSED',
            'ON_HOLD',
            'OUT_OF_STOCK'
        ));

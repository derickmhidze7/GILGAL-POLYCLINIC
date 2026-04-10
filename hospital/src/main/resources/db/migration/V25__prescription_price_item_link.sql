-- V25: Link prescriptions to service_price_items for direct TZS pricing
--      Also relax the NOT NULL on medication_id so price-catalogue-only
--      drugs (not yet in the medications master) can still be prescribed.

-- 1. Allow medication_id to be NULL (price-catalogue items may have no
--    matching row in medications yet)
ALTER TABLE prescriptions ALTER COLUMN medication_id DROP NOT NULL;

-- 2. Add optional FK → service_price_items for direct price lookup
ALTER TABLE prescriptions
    ADD COLUMN IF NOT EXISTS price_item_id UUID
        REFERENCES service_price_items(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_prescriptions_price_item_id
    ON prescriptions (price_item_id);

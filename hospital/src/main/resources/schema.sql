-- Drop the NOT NULL constraint on inventory_item_id so that the new StockBatch-based
-- dispense path (which does NOT set inventoryItem) can save DispensedItem rows.
-- continue-on-error=true in application.properties makes this idempotent on re-runs.
ALTER TABLE dispensed_items ALTER COLUMN inventory_item_id DROP NOT NULL;

-- Add the stock_batch_id FK column if it hasn't been created yet by Hibernate ddl-auto=update.
ALTER TABLE dispensed_items ADD COLUMN IF NOT EXISTS stock_batch_id UUID REFERENCES stock_batches(id) ON DELETE SET NULL;

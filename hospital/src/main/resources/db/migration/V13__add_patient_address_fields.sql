-- V13: Add permanent residence address columns to patients table.
-- The existing street / city / province / country / postal_code columns serve as the current location address.
ALTER TABLE patients ADD COLUMN IF NOT EXISTS perm_street      VARCHAR(255);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS perm_city        VARCHAR(100);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS perm_province    VARCHAR(100);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS perm_country     VARCHAR(100);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS perm_postal_code VARCHAR(20);

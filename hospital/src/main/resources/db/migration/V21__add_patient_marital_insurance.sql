ALTER TABLE patients ADD COLUMN IF NOT EXISTS marital_status          VARCHAR(30);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_provider       VARCHAR(150);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_policy_number  VARCHAR(100);
ALTER TABLE patients ADD COLUMN IF NOT EXISTS insurance_member_number  VARCHAR(100);

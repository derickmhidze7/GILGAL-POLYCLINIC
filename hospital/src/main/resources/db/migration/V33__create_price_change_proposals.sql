-- V33: Price-change proposals submitted by pharmacists, reviewed/approved by admin
CREATE TABLE IF NOT EXISTS price_change_proposals (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    price_item_id     UUID        REFERENCES service_price_items(id) ON DELETE SET NULL,
    product_name      VARCHAR(255) NOT NULL,
    product_code      VARCHAR(100),
    item_id           VARCHAR(100),
    classification    VARCHAR(200),
    category          VARCHAR(200),
    proposed_price    NUMERIC(12,2) NOT NULL,
    current_price     NUMERIC(12,2),              -- null for brand-new item proposals
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    proposed_by_id    UUID        REFERENCES staff(id) ON DELETE SET NULL,
    reviewed_by_id    UUID        REFERENCES staff(id) ON DELETE SET NULL,
    notes             VARCHAR(500),
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    reviewed_at       TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT now(),
    created_by        VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_price_proposals_status   ON price_change_proposals(status);
CREATE INDEX IF NOT EXISTS idx_price_proposals_proposer ON price_change_proposals(proposed_by_id);

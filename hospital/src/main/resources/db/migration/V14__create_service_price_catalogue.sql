-- ================================================================
--  V12 — Service Price Catalogue
--  Unified price list for PHARMACY, LAB, and SURGERY items.
--  Admin can add/update entries manually or via Excel upload.
-- ================================================================

CREATE TABLE IF NOT EXISTS service_price_items (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    item_id      VARCHAR(100),
    product_code VARCHAR(100),
    product_name VARCHAR(255)  NOT NULL,
    classification VARCHAR(200),
    type         VARCHAR(100)  NOT NULL,
    category     VARCHAR(200),
    price        NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT now(),
    created_by   VARCHAR(100),

    CONSTRAINT pk_service_price_items PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_spi_type     ON service_price_items (type);
CREATE INDEX IF NOT EXISTS idx_spi_category ON service_price_items (category);
CREATE INDEX IF NOT EXISTS idx_spi_code     ON service_price_items (product_code);

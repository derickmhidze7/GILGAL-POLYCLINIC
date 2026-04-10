-- V8: Create inventory_items and dispensed_items tables

CREATE TABLE inventory_items (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at       TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    medication_id    UUID         NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    batch_number     VARCHAR(80)  NOT NULL,
    quantity_in_stock INTEGER     NOT NULL,
    reorder_level    INTEGER      NOT NULL DEFAULT 10,
    unit_cost        NUMERIC(12,2) NOT NULL,
    expiry_date      DATE         NOT NULL,
    supplier         VARCHAR(200)
);

CREATE TABLE dispensed_items (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at         TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT now(),
    created_by         VARCHAR(100),
    prescription_id    UUID         NOT NULL REFERENCES prescriptions(id) ON DELETE RESTRICT,
    inventory_item_id  UUID         NOT NULL REFERENCES inventory_items(id) ON DELETE RESTRICT,
    quantity_dispensed INTEGER      NOT NULL,
    dispensed_by_id    UUID         NOT NULL REFERENCES staff(id) ON DELETE RESTRICT,
    dispensed_at       TIMESTAMP    NOT NULL DEFAULT now()
);

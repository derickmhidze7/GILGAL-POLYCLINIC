CREATE TABLE IF NOT EXISTS lab_stock_requests (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    requested_by_id     UUID         REFERENCES staff(id) ON DELETE SET NULL,
    stock_item_id       UUID         NOT NULL REFERENCES stock_items(id) ON DELETE RESTRICT,
    requested_quantity  INTEGER      NOT NULL,
    released_quantity   INTEGER,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    request_notes       VARCHAR(500),
    response_notes      VARCHAR(500),
    handled_by_id       UUID         REFERENCES staff(id) ON DELETE SET NULL,
    handled_at          TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lsr_status       ON lab_stock_requests(status);
CREATE INDEX IF NOT EXISTS idx_lsr_requested_by ON lab_stock_requests(requested_by_id);

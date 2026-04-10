-- V9: Create invoices, invoice_line_items, and payments tables

CREATE TABLE invoices (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    patient_id          UUID          NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    medical_record_id   UUID REFERENCES medical_records(id) ON DELETE SET NULL,
    invoice_number      VARCHAR(50)   NOT NULL UNIQUE,
    invoice_date        TIMESTAMP     NOT NULL DEFAULT now(),
    due_date            DATE,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    subtotal            NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    tax_amount          NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_amount        NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    notes               VARCHAR(1000),
    created_by_user_id  UUID REFERENCES app_users(id) ON DELETE SET NULL
);

CREATE TABLE invoice_line_items (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    invoice_id  UUID          NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description VARCHAR(300)  NOT NULL,
    category    VARCHAR(20)   NOT NULL,
    quantity    INTEGER       NOT NULL DEFAULT 1,
    unit_price  NUMERIC(12,2) NOT NULL,
    line_total  NUMERIC(12,2) NOT NULL
);

CREATE TABLE payments (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    created_by       VARCHAR(100),
    invoice_id       UUID          NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    amount_paid      NUMERIC(12,2) NOT NULL,
    payment_date     TIMESTAMP     NOT NULL DEFAULT now(),
    payment_method   VARCHAR(20)   NOT NULL,
    reference_number VARCHAR(100),
    received_by_id   UUID REFERENCES app_users(id) ON DELETE SET NULL
);

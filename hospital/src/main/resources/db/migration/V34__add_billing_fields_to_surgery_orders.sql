-- V34__add_billing_fields_to_surgery_orders.sql
ALTER TABLE surgery_orders
    ADD COLUMN sent_for_payment BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sent_for_payment_at TIMESTAMP WITHOUT TIME ZONE;

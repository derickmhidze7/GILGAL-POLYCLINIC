-- V24: Link surgery orders to their billing invoice
ALTER TABLE surgery_orders
    ADD COLUMN IF NOT EXISTS surgery_invoice_id UUID REFERENCES invoices(id);

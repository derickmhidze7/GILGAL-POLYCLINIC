-- V19: Expense Management Module
-- Creates: expenses, expense_settings, expense_budgets,
--          electricity_bills, water_bills, salary_records

-- ─────────────────────────────────────────────────────────────────────────────
--  MASTER EXPENSE TABLE
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE expenses (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),

    title               VARCHAR(255) NOT NULL,
    category            VARCHAR(50)  NOT NULL,
    amount              NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    expense_date        DATE         NOT NULL,
    payment_method      VARCHAR(30),
    paid_to             VARCHAR(200),
    receipt_number      VARCHAR(100),
    receipt_file_path   VARCHAR(500),
    recorded_by         VARCHAR(100) NOT NULL,
    notes               TEXT,

    -- Approval workflow
    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMP,
    approval_notes      TEXT,

    -- Recurring
    is_recurring        BOOLEAN      NOT NULL DEFAULT FALSE,
    recurring_frequency VARCHAR(20),

    -- Financial period
    financial_year      INT          NOT NULL,
    billing_month       INT          NOT NULL,

    -- Locking & source
    is_locked           BOOLEAN      NOT NULL DEFAULT FALSE,
    source_type         VARCHAR(30)  NOT NULL DEFAULT 'MANUAL'
);

-- ─────────────────────────────────────────────────────────────────────────────
--  EXPENSE SETTINGS (key-value config store)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE expense_settings (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT now(),
    created_by    VARCHAR(100),

    setting_key   VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(300)
);

-- Default settings
INSERT INTO expense_settings (setting_key, setting_value, description) VALUES
    ('APPROVAL_THRESHOLD',     '500000',  'Expenses above this TZS amount require approval before confirmation'),
    ('WATER_RATE_PER_UNIT',    '500',     'Default water rate per cubic metre (TZS)'),
    ('OVERTIME_RATE_PER_HOUR', '5000',    'Default overtime rate per hour (TZS)');

-- ─────────────────────────────────────────────────────────────────────────────
--  BUDGET MANAGEMENT
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE expense_budgets (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),

    category        VARCHAR(50)   NOT NULL,
    budget_period   VARCHAR(10)   NOT NULL,   -- MONTHLY or ANNUAL
    period_month    INT,                       -- 1-12, null for ANNUAL
    period_year     INT           NOT NULL,
    budget_amount   NUMERIC(14,2) NOT NULL DEFAULT 0.00,

    CONSTRAINT uq_expense_budget UNIQUE (category, budget_period, period_month, period_year)
);

-- ─────────────────────────────────────────────────────────────────────────────
--  ELECTRICITY BILLS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE electricity_bills (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now(),
    created_by        VARCHAR(100),

    billing_month     INT          NOT NULL,   -- 1-12
    billing_year      INT          NOT NULL,
    units_bought      NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    amount            NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    date_bought       DATE         NOT NULL,
    due_date          DATE,
    payment_status    VARCHAR(20)  NOT NULL DEFAULT 'UNPAID',
    bill_file_path    VARCHAR(500),
    notes             TEXT,

    expense_id        UUID REFERENCES expenses(id) ON DELETE SET NULL,

    CONSTRAINT uq_electricity_bill UNIQUE (billing_month, billing_year)
);

-- ─────────────────────────────────────────────────────────────────────────────
--  WATER BILLS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE water_bills (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now(),
    created_by        VARCHAR(100),

    billing_month     INT          NOT NULL,
    billing_year      INT          NOT NULL,
    previous_reading  NUMERIC(10,3) NOT NULL DEFAULT 0.000,
    current_reading   NUMERIC(10,3) NOT NULL DEFAULT 0.000,
    units_consumed    NUMERIC(10,3) NOT NULL DEFAULT 0.000,  -- auto-calc: current - previous
    rate_per_unit     NUMERIC(10,4) NOT NULL DEFAULT 500.0000,
    total_amount      NUMERIC(14,2) NOT NULL DEFAULT 0.00,   -- auto-calc: units * rate
    bill_reference    VARCHAR(100),
    due_date          DATE,
    payment_status    VARCHAR(20)  NOT NULL DEFAULT 'UNPAID',
    bill_file_path    VARCHAR(500),
    notes             TEXT,

    expense_id        UUID REFERENCES expenses(id) ON DELETE SET NULL,

    CONSTRAINT uq_water_bill UNIQUE (billing_month, billing_year)
);

-- ─────────────────────────────────────────────────────────────────────────────
--  SALARY RECORDS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE salary_records (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),

    staff_id            UUID        NOT NULL REFERENCES staff(id) ON DELETE RESTRICT,
    basic_salary        NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    overtime_hours      NUMERIC(6,2)  NOT NULL DEFAULT 0.00,
    overtime_rate       NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    total_overtime      NUMERIC(14,2) NOT NULL DEFAULT 0.00,  -- overtime_hours * overtime_rate
    total_payable       NUMERIC(14,2) NOT NULL DEFAULT 0.00,  -- basic_salary + total_overtime
    pay_period_month    INT          NOT NULL,
    pay_period_year     INT          NOT NULL,
    payment_method      VARCHAR(30),
    payment_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payment_date        DATE,
    proof_file_path     VARCHAR(500),
    notes               TEXT,

    expense_id          UUID REFERENCES expenses(id) ON DELETE SET NULL
);

-- ─────────────────────────────────────────────────────────────────────────────
--  PERFORMANCE INDEXES
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_expenses_expense_date     ON expenses(expense_date);
CREATE INDEX idx_expenses_category         ON expenses(category);
CREATE INDEX idx_expenses_status           ON expenses(status);
CREATE INDEX idx_expenses_recorded_by      ON expenses(recorded_by);
CREATE INDEX idx_expenses_period           ON expenses(financial_year, billing_month);
CREATE INDEX idx_salary_records_staff      ON salary_records(staff_id);
CREATE INDEX idx_salary_records_period     ON salary_records(pay_period_year, pay_period_month);

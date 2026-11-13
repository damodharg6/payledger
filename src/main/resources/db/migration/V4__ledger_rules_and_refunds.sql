-- V4: customer accounts scoped per merchant, "post once" rules, and refunds.
-- Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer

-- Customer accounts use owner_ref = '<merchant uuid>:<customer ref>' (36 + 1 + up to 100 chars),
-- so two merchants' "cust-1" never share an account.
ALTER TABLE ledger_accounts ALTER COLUMN owner_ref TYPE VARCHAR(200);

-- Defence in depth: a payment can be charged (PAYMENT) at most once and reversed at most once,
-- even if a bug in Java tried to post it twice. Refunds may happen many times.
CREATE UNIQUE INDEX ux_ledger_transactions_once_per_payment
    ON ledger_transactions (payment_id, type)
    WHERE type IN ('PAYMENT', 'REVERSAL');

CREATE TABLE refunds (
    id                     UUID PRIMARY KEY,
    payment_id             UUID         NOT NULL REFERENCES payments(id),
    merchant_id            UUID         NOT NULL REFERENCES merchants(id),
    amount_paise           BIGINT       NOT NULL CHECK (amount_paise > 0),
    reason                 VARCHAR(255),
    idempotency_key        VARCHAR(100) NOT NULL,
    ledger_transaction_id  UUID         NOT NULL REFERENCES ledger_transactions(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, idempotency_key)
);
CREATE INDEX idx_refunds_payment ON refunds (payment_id);

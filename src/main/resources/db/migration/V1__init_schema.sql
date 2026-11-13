-- PayLedger initial schema
-- Money is stored as BIGINT paise (1 rupee = 100 paise). Never use float or double for money.

CREATE TABLE merchants (
    id            UUID PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL UNIQUE,
    api_key_hash  VARCHAR(100) NOT NULL,
    webhook_url   VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    merchant_id     UUID        NOT NULL REFERENCES merchants(id),
    amount_paise    BIGINT      NOT NULL CHECK (amount_paise > 0),
    currency        CHAR(3)     NOT NULL DEFAULT 'INR',
    status          VARCHAR(20) NOT NULL
                    CHECK (status IN ('CREATED','PROCESSING','SUCCEEDED','FAILED','REVERSED')),
    customer_ref    VARCHAR(100) NOT NULL,
    bank_reference  VARCHAR(100),
    failure_reason  VARCHAR(255),
    reverse_by      TIMESTAMPTZ,              -- RBI T+1 deadline for failed payments
    version         BIGINT      NOT NULL DEFAULT 0,  -- optimistic locking
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at DESC);
CREATE INDEX idx_payments_status_reverse_by ON payments (status, reverse_by);

-- One row per (merchant, key): a retried request returns the stored result instead of charging again
CREATE TABLE idempotency_keys (
    merchant_id      UUID         NOT NULL REFERENCES merchants(id),
    idempotency_key  VARCHAR(100) NOT NULL,
    request_hash     CHAR(64)     NOT NULL,   -- SHA-256 of the request body; same key + different body = error
    payment_id       UUID         REFERENCES payments(id),
    response_code    INT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (merchant_id, idempotency_key)
);

CREATE TABLE ledger_accounts (
    id             UUID PRIMARY KEY,
    owner_type     VARCHAR(20) NOT NULL CHECK (owner_type IN ('CUSTOMER','MERCHANT','SYSTEM')),
    owner_ref      VARCHAR(100) NOT NULL,
    name           VARCHAR(120) NOT NULL,
    currency       CHAR(3)     NOT NULL DEFAULT 'INR',
    balance_paise  BIGINT      NOT NULL DEFAULT 0,  -- cached; the entries are the source of truth
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_type, owner_ref, currency)
);

CREATE TABLE ledger_transactions (
    id          UUID PRIMARY KEY,
    payment_id  UUID        REFERENCES payments(id),
    type        VARCHAR(20) NOT NULL CHECK (type IN ('PAYMENT','REVERSAL','REFUND')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only: every ledger transaction has entries whose debits equal its credits
CREATE TABLE ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID    NOT NULL REFERENCES ledger_transactions(id),
    account_id      UUID    NOT NULL REFERENCES ledger_accounts(id),
    direction       CHAR(1) NOT NULL CHECK (direction IN ('D','C')),
    amount_paise    BIGINT  NOT NULL CHECK (amount_paise > 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_entries_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_entries_txn ON ledger_entries (transaction_id);

-- Block any UPDATE or DELETE on ledger entries: mistakes are fixed with new reversing entries
CREATE FUNCTION forbid_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();

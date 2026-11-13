-- V3: every payment status change, for the dashboard timeline and for audits.
-- Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer

CREATE TABLE payment_status_history (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   UUID        NOT NULL REFERENCES payments(id),
    from_status  VARCHAR(20)
                 CHECK (from_status IN ('CREATED','PROCESSING','SUCCEEDED','FAILED','REVERSED')),
    to_status    VARCHAR(20) NOT NULL
                 CHECK (to_status IN ('CREATED','PROCESSING','SUCCEEDED','FAILED','REVERSED')),
    reason       VARCHAR(255),
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_status_history_payment ON payment_status_history (payment_id, id);

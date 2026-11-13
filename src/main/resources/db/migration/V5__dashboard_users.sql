-- V5: people who log in to the dashboard (JWT). Merchant servers keep using API keys.
-- Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer

CREATE TABLE dashboard_users (
    id             UUID PRIMARY KEY,
    merchant_id    UUID         NOT NULL REFERENCES merchants(id),
    email          VARCHAR(160) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,             -- BCrypt, never the password itself
    role           VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' CHECK (role IN ('ADMIN', 'VIEWER')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_dashboard_users_merchant ON dashboard_users (merchant_id);

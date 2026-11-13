-- V2: fast, unique API key lookup and the SYSTEM bank clearing account.
-- Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer

-- Every authenticated request looks a merchant up by the SHA-256 hash of its API key.
CREATE UNIQUE INDEX ux_merchants_api_key_hash ON merchants (api_key_hash);

-- Money taken from a customer whose payment then failed waits here until it is reversed.
-- After all reversals have run, this account's balance must be back to zero.
INSERT INTO ledger_accounts (id, owner_type, owner_ref, name, currency)
VALUES ('00000000-0000-0000-0000-000000000001', 'SYSTEM', 'BANK_CLEARING', 'Bank clearing account', 'INR');

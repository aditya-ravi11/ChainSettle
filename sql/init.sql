CREATE TABLE IF NOT EXISTS transaction_records (
    id              BIGSERIAL PRIMARY KEY,
    tx_id           VARCHAR(100) UNIQUE NOT NULL,
    tx_type         VARCHAR(40) NOT NULL,
    from_account_id VARCHAR(100),
    to_account_id   VARCHAR(100),
    from_org        VARCHAR(80),
    to_org          VARCHAR(80),
    amount          DECIMAL(18, 2) NOT NULL,
    currency        VARCHAR(20) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    org_initiator   VARCHAR(80) NOT NULL,
    fabric_tx_id    VARCHAR(255),
    block_number    BIGINT,
    settlement_ref  VARCHAR(100),
    metadata_json   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS account_snapshots (
    id            BIGSERIAL PRIMARY KEY,
    account_id    VARCHAR(100) NOT NULL,
    org_name      VARCHAR(80) NOT NULL,
    currency      VARCHAR(20) NOT NULL,
    account_type  VARCHAR(20) NOT NULL,
    asset_type    VARCHAR(80),
    balance       DECIMAL(18, 2) NOT NULL,
    snapshot_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS settlement_records (
    id              BIGSERIAL PRIMARY KEY,
    settlement_id   VARCHAR(100) UNIQUE NOT NULL,
    settlement_type VARCHAR(20) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    cash_leg_json   JSONB NOT NULL,
    asset_leg_json  JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tx_records_created_at ON transaction_records(created_at);
CREATE INDEX IF NOT EXISTS idx_tx_records_org_initiator ON transaction_records(org_initiator);
CREATE INDEX IF NOT EXISTS idx_tx_records_from_org ON transaction_records(from_org);
CREATE INDEX IF NOT EXISTS idx_tx_records_to_org ON transaction_records(to_org);
CREATE INDEX IF NOT EXISTS idx_tx_records_type ON transaction_records(tx_type);
CREATE INDEX IF NOT EXISTS idx_account_snapshots_time ON account_snapshots(snapshot_time);
CREATE INDEX IF NOT EXISTS idx_account_snapshots_account ON account_snapshots(account_id);
CREATE INDEX IF NOT EXISTS idx_settlement_records_status ON settlement_records(status);


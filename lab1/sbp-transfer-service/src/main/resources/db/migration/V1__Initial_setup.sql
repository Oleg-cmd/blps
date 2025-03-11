CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    sender_phone_number VARCHAR(10) NOT NULL,
    recipient_phone_number VARCHAR(10) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    recipient_bank_id VARCHAR(12) NOT NULL,
    recipient_bank_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    confirmation_code VARCHAR(6),
    sbp_transaction_id VARCHAR(50),
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_transfers_sender_phone ON transfers(sender_phone_number);
CREATE INDEX idx_transfers_created_at ON transfers(created_at);
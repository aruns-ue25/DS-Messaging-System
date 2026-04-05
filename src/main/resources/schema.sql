CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(255) PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    content TEXT,
    commit_version INT NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    client_request_id VARCHAR(255)
);

CREATE INDEX idx_messages_conv_version ON messages (conversation_id, commit_version DESC);

CREATE TABLE IF NOT EXISTS idempotency_records (
    conversation_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    client_request_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255),
    commit_version INT,
    final_status VARCHAR(50),
    created_at BIGINT NOT NULL,
    PRIMARY KEY (conversation_id, sender_id, client_request_id)
);

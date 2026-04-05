CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(255) NOT NULL,
    node_id VARCHAR(50) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    content TEXT,
    commit_version INT NOT NULL,
    created_at BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    client_request_id VARCHAR(255),
    PRIMARY KEY (id, node_id)
);

CREATE INDEX idx_messages_conv_node_version ON messages (conversation_id, node_id, commit_version DESC);

CREATE TABLE IF NOT EXISTS idempotency_records (
    conversation_id VARCHAR(191) NOT NULL,
    sender_id VARCHAR(191) NOT NULL,
    client_request_id VARCHAR(191) NOT NULL,
    node_id VARCHAR(50) NOT NULL,
    message_id VARCHAR(255),
    commit_version INT,
    final_status VARCHAR(50),
    created_at BIGINT NOT NULL,
    PRIMARY KEY (conversation_id, sender_id, client_request_id, node_id)
);

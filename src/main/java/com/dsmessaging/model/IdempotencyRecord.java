package com.dsmessaging.model;

public class IdempotencyRecord {
    private String messageId;
    private int commitVersion;
    private MessageStatus finalStatus;
    private long createdAt;

    public IdempotencyRecord(String messageId, int commitVersion, MessageStatus finalStatus, long createdAt) {
        this.messageId = messageId;
        this.commitVersion = commitVersion;
        this.finalStatus = finalStatus;
        this.createdAt = createdAt;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public int getCommitVersion() { return commitVersion; }
    public void setCommitVersion(int commitVersion) { this.commitVersion = commitVersion; }

    public MessageStatus getFinalStatus() { return finalStatus; }
    public void setFinalStatus(MessageStatus finalStatus) { this.finalStatus = finalStatus; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

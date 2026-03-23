package com.dsmessaging.model;

public class Message {
    private String messageId;
    private String conversationId;
    private String senderId;
    private String receiverId; 
    private String clientRequestId;
    private String content;
    private long timestamp;
    private int commitVersion;
    private MessageStatus status;
    private long createdAt;

    public Message(String messageId, String conversationId, String senderId, String receiverId, 
                   String clientRequestId, String content, long timestamp, int commitVersion, 
                   MessageStatus status, long createdAt) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.clientRequestId = clientRequestId;
        this.content = content;
        this.timestamp = timestamp;
        this.commitVersion = commitVersion;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getClientRequestId() { return clientRequestId; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }

    public int getCommitVersion() { return commitVersion; }
    public void setCommitVersion(int commitVersion) { this.commitVersion = commitVersion; }

    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + messageId + '\'' +
                ", conv='" + conversationId + '\'' +
                ", ver=" + commitVersion +
                ", status=" + status +
                ", content='" + content + '\'' +
                '}';
    }
}

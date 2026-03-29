package com.dsmessaging.model;

import java.util.Objects;

public class IdempotencyKey {
    private final String conversationId;
    private final String senderId;
    private final String clientRequestId;

    public IdempotencyKey(String conversationId, String senderId, String clientRequestId) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.clientRequestId = clientRequestId;
    }

    public String getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public String getClientRequestId() { return clientRequestId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKey that = (IdempotencyKey) o;
        return Objects.equals(conversationId, that.conversationId) &&
                Objects.equals(senderId, that.senderId) &&
                Objects.equals(clientRequestId, that.clientRequestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, senderId, clientRequestId);
    }

    @Override
    public String toString() {
        return "IdempotencyKey{" +
                "conversationId='" + conversationId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", clientRequestId='" + clientRequestId + '\'' +
                '}';
    }
}

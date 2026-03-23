package com.dsmessaging.model;

public class ClientWriteRequest {
    private String conversationId;
    private String senderId;
    private String receiverId;
    private String content;
    private String clientRequestId;

    public ClientWriteRequest(String conversationId, String senderId, String receiverId, String content, String clientRequestId) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.clientRequestId = clientRequestId;
    }

    public String getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public String getReceiverId() { return receiverId; }
    public String getContent() { return content; }
    public String getClientRequestId() { return clientRequestId; }
}

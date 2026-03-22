package com.dsmessaging.server;

import com.dsmessaging.model.Message;
import com.dsmessaging.service.MessageService;

public class ServerNode {
    private String serverId;
    private boolean active;
    private MessageService messageService;
    private com.dsmessaging.raft.RaftNode raftNode;

    public ServerNode(String serverId) {
        this.serverId = serverId;
        this.active = true;
        this.messageService = new MessageService();
        this.raftNode = new com.dsmessaging.raft.RaftNode(serverId, this);
    }

    public String getServerId() {
        return serverId;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
        raftNode.stop();
        System.out.println(serverId + " is now DOWN.");
    }

    public void activate() {
        this.active = true;
        raftNode.start();
        System.out.println(serverId + " is now ACTIVE.");
    }

    public com.dsmessaging.raft.RaftNode getRaftNode() {
        return raftNode;
    }

    public void receiveMessage(Message message) {
        if (!active) {
            System.out.println(serverId + " is down. Cannot receive message.");
            return;
        }

        System.out.println(serverId + " received message:");
        messageService.sendMessage(message);
    }

    public void displayMessages() {
        System.out.println("\nMessages stored in " + serverId + ":");

        if (messageService.getAllMessages().isEmpty()) {
            System.out.println("No messages stored.");
            return;
        }

        for (Message message : messageService.getAllMessages()) {
            System.out.println(message);
        }
    }

    public MessageService getMessageService() {
        return messageService;
    }
}

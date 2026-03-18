package com.dsmessaging.service;

import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;

import java.util.ArrayList;
import java.util.List;

public class MessagingSystem {
    private final List<ServerNode> servers;

    public MessagingSystem() {
        this.servers = new ArrayList<>();
    }

    public void addServer(ServerNode server) {
        servers.add(server);
        System.out.println(server.getServerId() + " added to the messaging system.");
    }

    public ServerNode findServerById(String serverId) {
        for (ServerNode server : servers) {
            if (server.getServerId().equals(serverId)) {
                return server;
            }
        }
        return null;
    }

    public void sendMessageToServer(String serverId, Message message) {
        ServerNode server = findServerById(serverId);

        if (server == null) {
            System.out.println("Server " + serverId + " not found.");
            return;
        }

        server.receiveMessage(message);
    }

    public void displayAllServerMessages() {
        System.out.println("\n=== DISPLAYING ALL SERVER MESSAGES ===");
        for (ServerNode server : servers) {
            server.displayMessages();
        }
    }

    public List<ServerNode> getServers() {
        return servers;
    }
}

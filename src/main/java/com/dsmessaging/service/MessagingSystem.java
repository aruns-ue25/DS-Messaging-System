package com.dsmessaging.service;

import com.dsmessaging.server.ServerNode;

import java.util.ArrayList;
import java.util.List;

public class MessagingSystem {
    private final List<ServerNode> servers;
    private final MetricsCollector globalMetrics;

    public MessagingSystem() {
        this.servers = new ArrayList<>();
        this.globalMetrics = new MetricsCollector();
    }

    public void addServer(String serverId) {
        ServerNode newServer = new ServerNode(serverId, globalMetrics);
        
        for (ServerNode existing : servers) {
            existing.addPeer(newServer);
            newServer.addPeer(existing);
        }
        
        servers.add(newServer);
        System.out.println(serverId + " added to the messaging system.");
    }

    public ServerNode getServer(String serverId) {
        for (ServerNode server : servers) {
            if (server.getServerId().equals(serverId)) return server;
        }
        throw new IllegalArgumentException("Server not found");
    }
    
    public void checkNodeHealth() {
        for (ServerNode node : servers) {
            if (!node.isActive()) {
                System.out.println("ClusterManager detected failure in Server " + node.getNodeId());
            }
        }
    }

    private ServerNode getActiveNode() {
        for (ServerNode node : servers) {
            if (node.isActive()) {
                return node;
            }
        }
        return null;
    }

    public void sendMessage(String message) {
        ServerNode target = getActiveNode();

        if (target == null) {
            System.out.println("No active servers available!");
            return;
        }

        target.storeMessage(message);
        replicateMessage(target, message);
    }

    public void replicateMessage(ServerNode source, String message) {
        for (ServerNode node : servers) {
            if (node != source && node.isActive()) {
                node.storeMessage(message);
            }
        }
    }
    
    public MetricsCollector getGlobalMetrics() {
        return globalMetrics;
    }
}
